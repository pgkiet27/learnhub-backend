package com.learnhub.enrollment.client.dto;

import java.time.Instant;
import java.util.UUID;

public record UserActivity(
        UUID userId,
        String email,
        Instant lastActiveAt,
        int activeDaysLast14,
        int activeDaysPrev14) {
}
