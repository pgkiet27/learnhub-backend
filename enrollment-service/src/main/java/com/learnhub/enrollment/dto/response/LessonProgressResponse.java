// dto/response/LessonProgressResponse.java
package com.learnhub.enrollment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class LessonProgressResponse {
    private UUID id;
    private UUID lessonId;
    // Lombok strips the "is" prefix from boolean getters, so the JSON name loses its "is" and no
    // longer matches the Frontend type. Pin the JSON name on the getter, as UserResponse does.
    @Getter(onMethod_ = @JsonProperty("isCompleted"))
    private boolean isCompleted;
    private Integer watchDurationSec;
    private Integer lastPositionSec;
    private Instant completedAt;
}