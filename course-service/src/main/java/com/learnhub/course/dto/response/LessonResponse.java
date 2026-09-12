package com.learnhub.course.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class LessonResponse {
    private UUID id;
    private String title;
    private String description;
    private String lessonType;
    private String videoUrl;
    private Integer videoDuration;
    private String documentUrl;
    private String content;
    private Integer displayOrder;
    private boolean isPreview;
    private boolean isPublished;
    private Instant publishedAt;
}