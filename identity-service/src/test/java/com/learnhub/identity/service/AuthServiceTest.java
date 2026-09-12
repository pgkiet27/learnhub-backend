package com.learnhub.identity.service;

import com.learnhub.common.event.UserRegisteredEvent;
import com.learnhub.identity.entity.User;
import com.learnhub.identity.repository.RefreshTokenRepository;
import com.learnhub.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;


@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService unit tests")
public class AuthServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtService jwtService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AuthService authService;

    private Jwt mockCognitoJwt;
    private User existingUser;

    @BeforeEach
    void setUp() {
        // Setup mock Cognito JWT
        mockCognitoJwt = Jwt.withTokenValue("cognito-token")
                .header("alg", "RS256")
                .subject("cognito-sub-123")
                .claim("email", "test@example.com")
                .claim("email_verified", true)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        // Setup existing user
        existingUser = User.builder()
                .id(UUID.randomUUID())
                .cognitoSub("cognito-sub-123")
                .email("test@example.com")
                .role(User.Role.student)
                .isEmailVerified(true)
                .build();
    }

    @Test
    @DisplayName("syncUser - User already existed - no need to create a new one, no publish event")
    void syncUser_UserExists_ShouldNotCreateNewUser() {
        // Given
        given(userRepository.existsByCognitoSub("cognito-sub-123")).willReturn(true);
        given(userRepository.findByCognitoSub("cognito-sub-123")).willReturn(Optional.of(existingUser));
        given(jwtService.generateAccessToken(any(), any(), any())).willReturn("learnhub-access-token");
        given(jwtService.generateRefreshToken(any())).willReturn("learnhub-refresh-token");
        given(jwtService.getAccessTokenExpiry()).willReturn(3600L);

        // When
        var response = authService.syncUser(mockCognitoJwt, "Chorme/Mac", "127.0.0.1");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUser().getEmail()).isEqualTo("test@example.com");
        assertThat(response.getUser().getRole()).isEqualTo("student");

        // Verify no publish event (user already existed)
        then(eventPublisher).should(never())
                .publishEvent(any(UserRegisteredEvent.class));
    }

    @Test
    @DisplayName("syncUser - new User - create user and publish UserRegisteredEvent")
    void syncUser_NewUser_ShouldCreateAndPublishEvent() {
        // Given
        given(userRepository.existsByCognitoSub("cognito-sub-123")).willReturn(false);
        given(userRepository.findByCognitoSub("cognito-sub-123"))
                .willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willReturn(existingUser);
        given(jwtService.generateAccessToken(any(), any(), any()))
                .willReturn("learnhub-access-token");
        given(jwtService.generateRefreshToken(any()))
                .willReturn("learnhub-refresh-token");
        given(jwtService.getAccessTokenExpiry()).willReturn(3600L);

        // When
        var response = authService.syncUser(mockCognitoJwt, null, "127.0.0.1");

        // Then
        assertThat(response.getAccessToken()).isEqualTo("learnhub-access-token");

        // Verify CÓ publish event (user mới)
        then(eventPublisher).should(times(1))
                .publishEvent(any(UserRegisteredEvent.class));
    }

    @Test
    @DisplayName("refreshAccessToken - expired Token - throw UnauthorizedException")
    void refreshAccessToken_ExpiredToken_ShouldThrowException(){
        // Given - token not exist in DB
        given(refreshTokenRepository.findByTokenHash(anyString()))
                .willReturn(Optional.empty());

        // When and Then
        assertThatThrownBy(() -> authService.refreshAccessToken("invalid-token"))
                .isInstanceOf(com.learnhub.common.exception.UnauthorizedException.class)
                .hasMessageContaining("Refresh token is invalid or expired");
    }
}
