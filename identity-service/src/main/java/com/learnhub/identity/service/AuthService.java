package com.learnhub.identity.service;

import com.learnhub.common.event.UserRegisteredEvent;
import com.learnhub.common.exception.ResourceNotFoundException;
import com.learnhub.common.exception.UnauthorizedException;
import com.learnhub.identity.dto.response.AuthResponse;
import com.learnhub.identity.dto.response.UserResponse;
import com.learnhub.identity.entity.RefreshToken;
import com.learnhub.identity.entity.User;
import com.learnhub.identity.repository.RefreshTokenRepository;
import com.learnhub.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Sync user from Cognito to DB
     * Flow:
     * 1. get Cognito JWT which is verfied by Spring Security
     * 2. get Cognito sub and email from JWT claims
     * 3. check if user exists in DB by Cognito sub
     * 4. if not exists, create new user + publish UserRegisteredEvent to RabbitMQ
     * 5. create Learnhub JWT (access token + refresh token)
     * 6. return AuthResponse
     */
    @Transactional
    public AuthResponse syncUser(Jwt cognitoJwt, String deviceInfo, String ipAddress){
        String cognitoSub = cognitoJwt.getSubject();
        String email = cognitoJwt.getClaimAsString("email");
        boolean isEmailVerified = Boolean.TRUE.equals(
                cognitoJwt.getClaimAsBoolean("email_verified")
        );

        // find or create user in DB
        boolean isNewUser = !userRepository.existsByCognitoSub(cognitoSub);
        User user = userRepository.findByCognitoSub(cognitoSub)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .cognitoSub(cognitoSub)
                            .email(email)
                            .role(User.Role.student) // default role is student
                            .isEmailVerified(isEmailVerified)
                            .build();
                    return userRepository.save(newUser);
                });

        // update email verified if changed
        if(user.isEmailVerified() != isEmailVerified){
            user.setEmailVerified(isEmailVerified);
            userRepository.save(user);
        }

        // publish event if new user
        if(isNewUser) {
            publishUserRegisteredEvent(user);
        }

        // generate Learnhub JWT
        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name()
        );
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        // save refresh token to DB
        saveRefreshToken(user, refreshToken, deviceInfo, ipAddress);

        log.info("User synced: {} ({})", email, isNewUser ? "new" : "existing");
        return buildAuthResponse(user, accessToken, refreshToken);
    }

    // exchange refresh token to get new access token
    @Transactional
    public AuthResponse refreshAccessToken(String refreshToken){
        String tokenHash = hashToken(refreshToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("INVALID_REFRESH_TOKEN",
                        "Refresh token is invalid or expired"));

        if(stored.getExpiresAt().isBefore(Instant.now())){
            refreshTokenRepository.delete(stored);
            throw new UnauthorizedException("EXPIRED_REFRESH_TOKEN",
                    "Refresh token is expired. Please login again.");
        }

        User user = stored.getUser();
        String newAccessToken = jwtService.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name()
        );
        return buildAuthResponse(user, newAccessToken, refreshToken);
    }

    // logout user by deleting refresh token
    @Transactional
    public void logout(UUID userId, String refreshToken){
        if(refreshToken != null) {
            String tokenHash = hashToken(refreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash)
                    .ifPresent(refreshTokenRepository::delete);
        } else {
            // logout from all devices
            refreshTokenRepository.deleteAllByUserId(userId);
        }
        log.info("User {} logged out", userId);
    }

    // get current user info
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId){
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND",
                        "User not found"));
        return UserResponse.builder()
                .id(user.getId().toString())
                .email(user.getEmail())
                .role(user.getRole().name())
                .isActive(user.isActive())
                .isEmailVerified(user.isEmailVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }

    // admin change user role
    @Transactional
    public UserResponse updateUserRole(UUID targetUserId, String newRole){
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND",
                        "User not found"));
        user.setRole(User.Role.valueOf(newRole.toLowerCase()));
        userRepository.save(user);

        log.info("User {} role changed to {}", targetUserId, newRole);

        return UserResponse.builder()
                .id(user.getId().toString())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    // private helpers
    private void publishUserRegisteredEvent(User user){
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
        // Publish via Spring's own event bus, not RabbitMQ directly - UserRegisteredEventListener
        // picks this up AFTER the DB transaction commits, so a RabbitMQ outage never rolls back
        // user creation (see UserRegisteredEventListener for the actual broker publish).
        eventPublisher.publishEvent(event);
    }

    private void saveRefreshToken(User user, String refreshToken, String deviceInfo, String ipAddress){
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(refreshToken))
                .deviceInfo(deviceInfo)
                .ipAddress(ipAddress)
                .expiresAt(Instant.now().plusSeconds(jwtService.getRefreshTokenExpiry()))
                .build();
        refreshTokenRepository.save(token);
    }

    private String hashToken(String token){
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken){
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getAccessTokenExpiry())
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId().toString())
                        .email(user.getEmail())
                        .role(user.getRole().name())
                        .isEmailVerified(user.isEmailVerified()
                        )
                        .build()
                )
                .build();
    }
}
