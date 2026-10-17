package com.learnhub.enrollment.churn;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

/** One row of the churn training dataset: an enrollment's features and prediction at a scoring run. */
public record ChurnSnapshot(
        UUID enrollmentId,
        UUID userId,
        UUID courseId,
        Instant enrolledAt,
        Map<String, Double> features,
        String missingSources,
        BigDecimal churnScore,
        String riskLevel,
        String modelUsed,
        boolean reminderQueued) {

    /** Same zone as the nightly schedule, so one run maps to one date partition. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public static LocalDate dateOf(Instant at) {
        return LocalDate.ofInstant(at, ZONE);
    }
}
