package com.learnhub.course.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTranscriptRequest {

    // null or blank clears the transcript
    @Size(max = 200_000)
    private String transcript;
}
