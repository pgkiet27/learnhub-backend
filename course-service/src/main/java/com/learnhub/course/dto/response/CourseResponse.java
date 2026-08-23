package com.learnhub.course.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CourseResponse {
    private UUID id;
    private UUID instructorId;
    private String title;
    private String slug;
    private String shortDescription;
    private String description;
    private String thumbnailUrl;
    private String previewVideoUrl;
    private String level;
    private String language;
    private BigDecimal price;
    private BigDecimal discountPrice;
    private String status;
    private String[] tags;
    private String[] requirements;
    private String[] objectives;
    private Integer totalLessons;
    private Integer totalDuration;
    private Integer totalStudents;
    private Integer totalReviews;
    private BigDecimal avgRating;
    private boolean isBestseller;
    private boolean isFeatured;
    private Instant publishedAt;
    private Instant createdAt;
    private CategoryInfo category;

    @Data
    @Builder
    public static class CategoryInfo {
        private UUID id;
        private String name;
        private String slug;
    }
}