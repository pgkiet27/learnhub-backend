package com.learnhub.course.dto.response;

import lombok.Builder;
import lombok.Data;

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
    private boolean isResolved;
    private Instant createdAt;
    private List<QaAnswerResponse> answers;
}
