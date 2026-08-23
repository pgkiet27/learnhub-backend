package com.learnhub.course.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class QaAnswerResponse {
    private UUID id;
    private UUID questionId;
    private UUID userId;
    private String content;
    private boolean isInstructor;
    private boolean isAccepted;
    private int upvoteCount;
    private Instant createdAt;
}
