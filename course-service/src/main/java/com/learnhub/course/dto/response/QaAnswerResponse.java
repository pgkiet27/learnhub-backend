package com.learnhub.course.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class QaAnswerResponse {
    private UUID id;
    private UUID questionId;
    private UUID userId;
    private String content;
    // Lombok strips the "is" prefix from boolean getters, so the JSON name loses its "is" and no
    // longer matches the Frontend type. Pin the JSON name on the getter, as UserResponse does.
    @Getter(onMethod_ = @JsonProperty("isInstructor"))
    private boolean isInstructor;
    @Getter(onMethod_ = @JsonProperty("isAccepted"))
    private boolean isAccepted;
    private int upvoteCount;
    private Instant createdAt;
}
