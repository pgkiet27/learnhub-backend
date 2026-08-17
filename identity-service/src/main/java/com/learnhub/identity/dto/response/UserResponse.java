package com.learnhub.identity.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Builder
@Data
public class UserResponse {
    private String id;
    private String email;
    private String role;
    private boolean isEmailVerified;
    private boolean isActive;
    private Instant createdAt;
}
