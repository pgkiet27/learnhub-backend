package com.learnhub.enrollment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class CourseInfoResponse {
    private UUID id;
    private String title;
    private String thumbnailUrl;
    private BigDecimal price;
    private String status;
    private UUID instructorId;
    private Integer totalLessons;
}