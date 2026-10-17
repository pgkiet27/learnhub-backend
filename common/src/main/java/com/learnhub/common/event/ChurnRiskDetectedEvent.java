package com.learnhub.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published by Enrollment Service when a student is scored as high churn risk (routing key churn.high_risk).
 * Consumer: Notification Service → learning reminder email.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChurnRiskDetectedEvent {
    private UUID enrollmentId;
    private UUID userId;
    private UUID courseId;
    private String courseTitle;
    private BigDecimal progressPercent;
    private BigDecimal churnScore;
    private Instant lastAccessedAt;

    @Builder.Default
    private Instant occurredAt = Instant.now();
}
