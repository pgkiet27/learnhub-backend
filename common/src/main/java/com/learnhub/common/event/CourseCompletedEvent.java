package com.learnhub.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a student completes 100% of a course.
 * Consumer: Certificate Service (B6) → auto-issues a certificate.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseCompletedEvent {
    private UUID enrollmentId;
    private UUID userId;
    private UUID courseId;
    private String courseTitle;

    @Builder.Default
    private Instant occurredAt = Instant.now();
}