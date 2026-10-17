package com.learnhub.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateLessonRequest {

    @NotBlank(message = "Tiêu đề bài học không được để trống")
    @Size(max = 300)
    private String title;

    private String description;

    // video | document | text
    private String lessonType = "video";

    private Boolean isPreview = false;
}