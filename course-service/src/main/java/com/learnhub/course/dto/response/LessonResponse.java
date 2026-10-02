package com.learnhub.course.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

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
    private String transcript;
    private Integer displayOrder;
    // Lombok strips the "is" prefix from boolean getters, so the JSON name loses its "is" and no
    // longer matches the Frontend type. Pin the JSON name on the getter, as UserResponse does.
    @Getter(onMethod_ = @JsonProperty("isPreview"))
    private boolean isPreview;
    @Getter(onMethod_ = @JsonProperty("isPublished"))
    private boolean isPublished;
    private Instant publishedAt;
}