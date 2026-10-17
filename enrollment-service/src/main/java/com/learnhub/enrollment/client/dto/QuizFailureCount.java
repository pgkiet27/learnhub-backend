package com.learnhub.enrollment.client.dto;

import java.util.UUID;

public record QuizFailureCount(UUID userId, UUID courseId, long failedAttempts) {
}
