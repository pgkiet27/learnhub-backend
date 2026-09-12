package com.learnhub.identity.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String accessToken; // Learnhub JWT (not Cognito JWT)
    private String refreshToken; // Learnhub refresh token
    private long expiresIn; // in seconds when the access token will expire
    private UserInfo user;

    @Data
    @Builder
    public static class UserInfo {
        private String id;
        private String email;
        private String role;
        private boolean isEmailVerified;

    }
}