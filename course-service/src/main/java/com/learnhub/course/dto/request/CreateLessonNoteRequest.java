package com.learnhub.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateLessonNoteRequest {
    @NotBlank
    @Size(max = 5000)
    private String content;
    private Integer timestampSec;  // Position in the video (seconds)
}
