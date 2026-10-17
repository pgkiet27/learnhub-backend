package com.learnhub.course.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ReviewResponse {
    private UUID id;
    private UUID courseId;
    private UUID userId;
    private Short rating;
    private String comment;
    // Lombok strips the "is" prefix from boolean getters, so the JSON name loses its "is" and no
    // longer matches the Frontend type. Pin the JSON name on the getter, as UserResponse does.
    @Getter(onMethod_ = @JsonProperty("isVisible"))
    private boolean isVisible;
    private Instant createdAt;
    private Instant updatedAt;
}
