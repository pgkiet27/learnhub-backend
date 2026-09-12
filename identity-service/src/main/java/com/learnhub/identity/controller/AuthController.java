package com.learnhub.identity.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.identity.dto.response.AuthResponse;
import com.learnhub.identity.dto.response.UserResponse;
import com.learnhub.identity.security.CurrentUser;
import com.learnhub.identity.service.AuthService;
import com.learnhub.identity.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Authentication", description = "Cognito OAuth2 sync and JWT management")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final JwtService jwtService;

    /*
     * POST api/v1/auth/sync
     *
     * Called after frontend receives Cognito JWT from user login
     * Identity Service will verify Cognito JWT, then generate Learnhub JWT and sync user into DB
     *
     * Header: Authorization: Bearer <Cognito JWT>
     */
    @Operation(
            summary = "Sync user from Cognito",
            description = "Verify Cognito token -> create/find user in DB -> return Learnhub JWT",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<AuthResponse>> syncUser(
            @AuthenticationPrincipal Jwt cognitoJwt,
            @RequestHeader(value = "X-Device-Info", required = false) String deviceInfo,
            HttpServletRequest request
    ) {
        String ipAddress = request.getRemoteAddr();
        AuthResponse authResponse = authService.syncUser(cognitoJwt, deviceInfo, ipAddress);
        return ResponseEntity.ok(
                ApiResponse.success(authResponse, "Login successfully")
        );
    }

    /*
     * POST api/v1/auth/refresh
     * Exchange refresh token for new access token. No need Cognito token here
     */
    @Operation(summary = "Refresh access token")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @RequestHeader("X-Refresh-Token") String refreshToken
    ) {
        AuthResponse authResponse = authService.refreshAccessToken(refreshToken);
        return ResponseEntity.ok(
                ApiResponse.success(authResponse, "Token refreshed successfully")
        );
    }

    /*
     * POST api/v1/auth/logout
     * Delete refresh token from DB.
     * Header: Authorization: Bearer <Learnhub access token>
     */
    @Operation(
            summary = "Logout",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken
    ){
        // get userId from Learnhub access token in SecurityContext
        UUID userId = UUID.fromString(
                CurrentUser.getJwt().getClaimAsString("userId")
        );
        authService.logout(userId, refreshToken);
        return ResponseEntity.ok(ApiResponse.success(null, "Logout sucessfully!"));
    }

    /*
     * GET /api/v1/auth/me
     * Get current user
     */
    @Operation(
            summary = "Get current user information",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(){
        UUID userId = UUID.fromString(
                CurrentUser.getJwt().getClaimAsString("userId")
        );
        UserResponse user = authService.getCurrentUser(userId);
        return ResponseEntity.ok(ApiResponse.success(user, "OK"));
    }
}
