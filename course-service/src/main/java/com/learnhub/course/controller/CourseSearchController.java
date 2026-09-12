package com.learnhub.course.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.common.dto.PageResponse;
import com.learnhub.course.dto.response.CourseResponse;
import com.learnhub.course.service.CourseSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "Search", description = "Tìm kiếm khóa học")
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseSearchController {

    private final CourseSearchService searchService;

    /**
     * GET /api/v1/courses/search
     * <p>
     * Query params:
     * keyword    = search keyword
     * categoryId = category UUID
     * level      = beginner | intermediate | advanced | all
     * minPrice   = minimum price
     * maxPrice   = maximum price
     * minRating  = minimum rating (1-5)
     * sortBy     = createdAt | price | avgRating | totalStudents
     * sortDir    = asc | desc
     * page       = page number (starting from 0)
     * size       = items per page
     * <p>
     * Example:
     * GET /api/v1/courses/search?keyword=java&level=beginner&maxPrice=500000&sortBy=avgRating&sortDir=desc
     */
    @Operation(
            summary = "Tìm kiếm khóa học",
            description = "Hỗ trợ full-text search, filter theo category/level/price/rating, sort đa tiêu chí"
    )
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<CourseResponse>>> searchCourses(
            @Parameter(description = "Từ khóa tìm kiếm")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "UUID của danh mục")
            @RequestParam(required = false) String categoryId,

            @Parameter(description = "Cấp độ: beginner | intermediate | advanced | all")
            @RequestParam(required = false) String level,

            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Double minRating,

            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        PageResponse<CourseResponse> result = searchService.searchCourses(
                keyword, categoryId, level,
                minPrice, maxPrice, minRating,
                PageRequest.of(page, size, sort));

        return ResponseEntity.ok(ApiResponse.success(result, "OK"));
    }

    @Operation(summary = "Lấy khóa học nổi bật (Top 10 bestseller)")
    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<List<CourseResponse>>> getFeaturedCourses() {
        return ResponseEntity.ok(
                ApiResponse.success(searchService.getFeaturedCourses(), "OK"));
    }

    @Operation(summary = "Lấy khóa học theo danh mục")
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<PageResponse<CourseResponse>>> getCoursesByCategory(
            @PathVariable String categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "totalStudents") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        PageResponse<CourseResponse> result = searchService.getCoursesByCategory(
                categoryId, PageRequest.of(page, size, sort));

        return ResponseEntity.ok(ApiResponse.success(result, "OK"));
    }
}