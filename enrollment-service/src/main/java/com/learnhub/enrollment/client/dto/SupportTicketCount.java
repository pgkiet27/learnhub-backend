package com.learnhub.enrollment.client.dto;

import java.util.UUID;

public record SupportTicketCount(UUID userId, long ticketsLast30Days) {
}
