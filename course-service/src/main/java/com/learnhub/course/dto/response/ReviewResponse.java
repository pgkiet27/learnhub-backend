package com.learnhub.course.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ReviewResponse {
    private UUID id;
    private UUID courseId;
    private UUID userId;
    private Short rating;
    private String comment;
    private boolean isVisible;
    private Instant createdAt;
    private Instant updatedAt;
}
