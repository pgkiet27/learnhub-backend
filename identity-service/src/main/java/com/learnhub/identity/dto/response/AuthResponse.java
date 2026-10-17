package com.learnhub.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

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
        // Lombok strips the "is" prefix from boolean getters, so Jackson would emit
        // "emailVerified"; pin the JSON name the frontend expects. Must go on the generated
        // getter: on the field it would be serialized in addition to "emailVerified".
        @Getter(onMethod_ = @JsonProperty("isEmailVerified"))
        private boolean isEmailVerified;

    }
}