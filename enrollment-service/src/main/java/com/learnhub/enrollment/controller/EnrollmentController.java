package com.learnhub.enrollment.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.enrollment.dto.response.EnrollmentResponse;
import com.learnhub.enrollment.dto.response.EnrollmentStatusResponse;
import com.learnhub.enrollment.service.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Enrollments", description = "Enroll / unenroll from courses")
@RestController
@RequestMapping("/api/v1/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

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
}