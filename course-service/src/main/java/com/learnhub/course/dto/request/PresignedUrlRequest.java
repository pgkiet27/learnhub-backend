package com.learnhub.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PresignedUrlRequest {

    @NotBlank(message = "Tên file không được để trống")
    private String fileName;

    @NotBlank(message = "Content type không được để trống")
    private String contentType;

    // video | thumbnail | document
    private String fileType = "video";
}