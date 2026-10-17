package com.learnhub.course.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CategoryResponse {
    private UUID id;
    private String name;
    private String slug;
    private String icon;
    private Integer displayOrder;
    // Number of published courses attached directly to this category. Courses of a child
    // category are NOT rolled up into the parent, so this always matches what the course
    // search returns for the same categoryId (it filters on category_id = ? as well).
    private Integer courseCount;
    private List<CategoryResponse> children;
}