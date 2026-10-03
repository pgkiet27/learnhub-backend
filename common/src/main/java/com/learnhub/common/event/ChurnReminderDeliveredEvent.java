package com.learnhub.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by Notification Service once a churn reminder email has actually been sent
 * (routing key churn.reminder_delivered). Not published when the student opted out or has no email.
 * Consumer: Enrollment Service → enrollments.churn_reminder_delivered_at, used to label the churn dataset.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChurnReminderDeliveredEvent {
    private UUID enrollmentId;
    private Instant deliveredAt;
}
