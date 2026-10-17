package com.learnhub.enrollment.client.dto;

import java.util.UUID;

public record UserSummary(UUID userId, String fullName, String avatarUrl, boolean emailLearningReminder) {
}
