package com.learnhub.assessment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Full quiz content; on update the questions replace the existing ones. */
@Data
public class QuizRequest {
    @NotNull
    private UUID courseId;

    private UUID lessonId;

    @NotBlank
    @Size(max = 255)
    private String title;

    @Size(max = 5000)
    private String description;

    @NotNull
    @Min(0)
    @Max(100)
    private Integer passScore;

    @Positive
    private Integer timeLimitSec;

    // null = unlimited
    @Positive
    private Integer maxAttempts;

    @NotEmpty
    @Size(max = 100)
    @Valid
    private List<QuestionRequest> questions;

    @Data
    public static class QuestionRequest {
        @NotBlank
        @Size(max = 5000)
        private String content;

        @Size(max = 5000)
        private String explanation;

        @NotNull
        @Size(min = 2, max = 10)
        @Valid
        private List<OptionRequest> options;
    }

    @Data
    public static class OptionRequest {
        @NotBlank
        @Size(max = 2000)
        private String content;

        private boolean correct;
    }
}
