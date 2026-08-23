package com.learnhub.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateSectionRequest {

    @NotBlank(message = "Tiêu đề section không được để trống")
    @Size(max = 300)
    private String title;

    private String description;
}