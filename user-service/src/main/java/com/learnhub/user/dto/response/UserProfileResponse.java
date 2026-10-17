package com.learnhub.user.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class UserProfileResponse {
    private String userId;
    private String fullName;
    private String avatarUrl;
    private String bio;
    private String headline;
    private String websiteUrl;
    private String language;
    private String timezone;
    private Instant createdAt;
    private Instant updatedAt;
}
