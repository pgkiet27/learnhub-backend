package com.learnhub.enrollment.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.enrollment.dto.response.EnrollmentResponse;
import com.learnhub.enrollment.dto.response.EnrollmentStatusResponse;
import com.learnhub.enrollment.service.EnrollmentService;
import com.learnhub.enrollment.service.LessonProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Enrollments", description = "Enroll / unenroll from courses")
@RestController
@RequestMapping("/api/v1/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;
    private final LessonProgressService progressService;

    @Operation(summary = "Enroll in a free course")
    @PostMapping("/{courseId}")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> enrollFreeCourse(
            @PathVariable UUID courseId,
            @RequestHeader("X-User-Id") UUID userId) {

        EnrollmentResponse response = enrollmentService.enrollFreeCourse(userId, courseId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Enrolled in course successfully"));
    }

    @Operation(summary = "Unenroll from a course")
    @DeleteMapping("/{courseId}")
    public ResponseEntity<ApiResponse<Void>> unenrollCourse(
            @PathVariable UUID courseId,
            @RequestHeader("X-User-Id") UUID userId) {

        enrollmentService.unenrollCourse(userId, courseId);
        return ResponseEntity.ok(ApiResponse.success(null, "Unenrolled successfully"));
    }

    @Operation(summary = "Check whether the course is already enrolled")
    @GetMapping("/{courseId}/status")
    public ResponseEntity<ApiResponse<EnrollmentStatusResponse>> getEnrollmentStatus(
            @PathVariable UUID courseId,
            @RequestHeader("X-User-Id") UUID userId) {

        return ResponseEntity.ok(ApiResponse.success(
                enrollmentService.getEnrollmentStatus(userId, courseId), "OK"));
    }

    @Operation(summary = "List IDs of completed lessons in the course")
    @GetMapping("/{courseId}/completed-lessons")
    public ResponseEntity<ApiResponse<List<UUID>>> getCompletedLessons(
            @PathVariable UUID courseId,
            @RequestHeader("X-User-Id") UUID userId) {

        return ResponseEntity.ok(ApiResponse.success(
                progressService.getCompletedLessonIds(userId, courseId), "OK"));
    }
}