package com.learnhub.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthSyncRequest {
    @NotBlank(message = "Cognito access token is required")
    private String cognitoAccessToken;

    // Optional: device information for logging or analytics
    private String deviceInfo;
}
