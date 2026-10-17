package com.learnhub.course.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class QaQuestionResponse {
    private UUID id;
    private UUID lessonId;
    private UUID userId;
    private String content;
    private int upvoteCount;
    // Lombok strips the "is" prefix from boolean getters, so the JSON name loses its "is" and no
    // longer matches the Frontend type. Pin the JSON name on the getter, as UserResponse does.
    @Getter(onMethod_ = @JsonProperty("isResolved"))
    private boolean isResolved;
    private Instant createdAt;
    private List<QaAnswerResponse> answers;
}
