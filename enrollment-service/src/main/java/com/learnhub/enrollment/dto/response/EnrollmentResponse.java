// dto/response/EnrollmentResponse.java
package com.learnhub.enrollment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class EnrollmentResponse {
    private UUID id;
    private UUID userId;
    private UUID courseId;
    private String courseTitle;
    private String courseThumbnailUrl;
    private Integer totalLessons;
    private Integer completedLessons;
    private BigDecimal progressPercent;
    private boolean isCompleted;
    private Instant enrolledAt;
    private Instant completedAt;
    private Instant lastAccessedAt;
}