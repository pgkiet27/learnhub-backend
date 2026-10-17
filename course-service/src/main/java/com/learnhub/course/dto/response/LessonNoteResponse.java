package com.learnhub.course.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class LessonNoteResponse {
    private UUID id;
    private UUID lessonId;
    private UUID userId;
    private String content;
    private Integer timestampSec;
    private Instant createdAt;
    private Instant updatedAt;
}
