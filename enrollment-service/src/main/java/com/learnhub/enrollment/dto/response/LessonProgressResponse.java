// dto/response/LessonProgressResponse.java
package com.learnhub.enrollment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class LessonProgressResponse {
    private UUID id;
    private UUID lessonId;
    private boolean isCompleted;
    private Integer watchDurationSec;
    private Integer lastPositionSec;
    private Instant completedAt;
}