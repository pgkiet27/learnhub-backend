package com.learnhub.assessment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class SubmitAttemptRequest {
    /** questionId → chosen optionId; unanswered questions are simply missing (and count as wrong). */
    @NotNull
    private Map<UUID, UUID> answers;

    @PositiveOrZero
    private Integer timeSpentSec;
}
