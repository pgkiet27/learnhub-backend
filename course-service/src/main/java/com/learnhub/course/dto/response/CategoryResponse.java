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
    private List<CategoryResponse> children;
}