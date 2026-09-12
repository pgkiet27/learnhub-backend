package com.learnhub.course.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.common.exception.ResourceNotFoundException;
import com.learnhub.course.dto.response.CourseInternalResponse;
import com.learnhub.course.entity.Course;
import com.learnhub.course.repository.CourseRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal endpoint — only meant to be called by other services (Enrollment, Payment...),
 * not by the Frontend. No X-User-Id is needed since this isn't a user-initiated action.
 */
@Tag(name = "Internal", description = "APIs used internally between services")
@RestController
@RequestMapping("/api/v1/internal/courses")
@RequiredArgsConstructor
public class CourseInternalController {

    private final CourseRepository courseRepository;

    @Operation(summary = "Get minimal course info by ID (for internal use)")
    @GetMapping("/{courseId}")
    public ResponseEntity<ApiResponse<CourseInternalResponse>> getCourseInternal(
            @PathVariable UUID courseId) {

        Course course = courseRepository.findById(courseId)
                .filter(c -> c.getStatus() == Course.Status.published)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "COURSE_NOT_FOUND", "Course does not exist or is not published"));

        CourseInternalResponse response = CourseInternalResponse.builder()
                .id(course.getId())
                .title(course.getTitle())
                .thumbnailUrl(course.getThumbnailUrl())
                .price(course.getPrice())
                .status(course.getStatus().name())
                .instructorId(course.getInstructorId())
                .totalLessons(course.getTotalLessons())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, "OK"));
    }
}