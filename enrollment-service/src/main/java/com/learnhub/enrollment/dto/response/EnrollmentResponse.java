// dto/response/EnrollmentResponse.java
package com.learnhub.enrollment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

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
    // Lombok strips the "is" prefix from boolean getters, so the JSON name loses its "is" and no
    // longer matches the Frontend type. Pin the JSON name on the getter, as UserResponse does.
    @Getter(onMethod_ = @JsonProperty("isCompleted"))
    private boolean isCompleted;
    private Instant enrolledAt;
    private Instant completedAt;
    private Instant lastAccessedAt;
}