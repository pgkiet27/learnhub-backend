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
public class CourseApprovedEvent {
    private UUID courseId;
    private UUID instructorId;
    private String courseTitle;

    @Builder.Default
    private Instant occurredAt = Instant.now();
}
