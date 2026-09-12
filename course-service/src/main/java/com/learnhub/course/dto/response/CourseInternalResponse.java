package com.learnhub.course.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class CourseInternalResponse {
    private UUID id;
    private String title;
    private String thumbnailUrl;
    private BigDecimal price;
    private String status;
    private UUID instructorId;
    private Integer totalLessons;
}