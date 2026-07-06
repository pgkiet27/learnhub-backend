package com.learnhub.common.event;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentCreatedEvent {
    private UUID enrollmentId;
    private UUID userId;
    private UUID courseId;

    @Builder.Default
    private Instant occurredAt = Instant.now();
}
