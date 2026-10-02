package com.learnhub.course.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
// Needed to read the object back from the Redis cache (see PageResponse)
@Jacksonized
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
    // Lombok strips the "is" prefix from boolean getters, so the JSON defaults to
    // "bestseller"/"featured", which does not match the Course type on the Frontend. Pin the name
    // on the getter (not the field, or both names get serialized) — same as identity-service.
    @Getter(onMethod_ = @JsonProperty("isBestseller"))
    private boolean isBestseller;

    @Getter(onMethod_ = @JsonProperty("isFeatured"))
    private boolean isFeatured;
    private Instant publishedAt;
    private Instant createdAt;
    private CategoryInfo category;

    @Data
    @Builder
    @Jacksonized
    public static class CategoryInfo {
        private UUID id;
        private String name;
        private String slug;
    }
}