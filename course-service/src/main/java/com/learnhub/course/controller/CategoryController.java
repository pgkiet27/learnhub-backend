package com.learnhub.course.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.course.dto.response.CategoryResponse;
import com.learnhub.course.entity.Category;
import com.learnhub.course.repository.CategoryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "Categories", description = "Danh mục khóa học")
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;

    @Operation(summary = "Lấy tất cả danh mục (kèm danh mục con)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategories() {
        List<CategoryResponse> categories = categoryRepository.findAllRootCategories()
                .stream()
                .map(this::toCategoryResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(categories, "OK"));
    }

    private CategoryResponse toCategoryResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .icon(category.getIcon())
                .displayOrder(category.getDisplayOrder())
                .children(category.getChildren().stream()
                        .filter(Category::isActive)
                        .map(child -> CategoryResponse.builder()
                                .id(child.getId())
                                .name(child.getName())
                                .slug(child.getSlug())
                                .icon(child.getIcon())
                                .displayOrder(child.getDisplayOrder())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
}