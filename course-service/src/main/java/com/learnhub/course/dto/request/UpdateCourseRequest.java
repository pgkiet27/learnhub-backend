package com.learnhub.course.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class UpdateCourseRequest {

    @Size(max = 300, message = "Tiêu đề tối đa 300 ký tự")
    private String title;

    @Size(max = 500, message = "Mô tả ngắn tối đa 500 ký tự")
    private String shortDescription;

    private String description;

    private UUID categoryId;

    @DecimalMin(value = "0", message = "Giá phải >= 0")
    private BigDecimal price;

    private BigDecimal discountPrice;

    private String thumbnailUrl;
    private String previewVideoUrl;

    private List<String> tags;
    private List<String> requirements;
    private List<String> objectives;
}
