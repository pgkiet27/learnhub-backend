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
public class LessonPublishedEvent {
    private UUID lessonId;
    private UUID courseId;
    private String title;
    private String content;

    @Builder.Default
    private Instant occurredAt = Instant.now();
}
