package com.learnhub.user.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SupportTicketResponse(
        UUID id,
        UUID userId,
        String userFullName,
        UUID courseId,
        String category,
        String subject,
        String message,
        String status,
        String adminReply,
        Instant repliedAt,
        Instant createdAt,
        Instant updatedAt) {
}
