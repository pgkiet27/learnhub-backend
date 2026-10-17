package com.learnhub.course.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.common.dto.PageResponse;
import com.learnhub.course.dto.response.CourseResponse;
import com.learnhub.course.service.CourseApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Course Approval", description = "Workflow duyệt khóa học")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CourseApprovalController {

    private final CourseApprovalService approvalService;

    // Instructor Actions

    @Operation(summary = "Instructor submit khóa học để admin duyệt")
    @PostMapping("/courses/{courseId}/submit")
    public ResponseEntity<ApiResponse<CourseResponse>> submitForReview(
            @PathVariable UUID courseId,
            @RequestHeader("X-User-Id") UUID instructorId) {

        return ResponseEntity.ok(ApiResponse.success(
                approvalService.submitForReview(courseId, instructorId),
                "Đã submit khóa học để duyệt"));
    }

    @Operation(summary = "Instructor rút khóa học về draft")
    @PostMapping("/courses/{courseId}/unpublish")
    public ResponseEntity<ApiResponse<CourseResponse>> unpublish(
            @PathVariable UUID courseId,
            @RequestHeader("X-User-Id") UUID instructorId) {

        return ResponseEntity.ok(ApiResponse.success(
                approvalService.unpublish(courseId, instructorId),
                "Đã unpublish khóa học"));
    }

    // Admin Actions

    @Operation(summary = "Admin lấy danh sách khóa học đang pending")
    @GetMapping("/admin/courses/pending")
    public ResponseEntity<ApiResponse<PageResponse<CourseResponse>>> getPendingCourses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return ResponseEntity.ok(ApiResponse.success(
                approvalService.getPendingCourses(PageRequest.of(page, size)), "OK"));
    }

    @Operation(summary = "Admin duyệt khóa học")
    @PostMapping("/admin/courses/{courseId}/approve")
    public ResponseEntity<ApiResponse<CourseResponse>> approveCourse(
            @PathVariable UUID courseId) {

        return ResponseEntity.ok(ApiResponse.success(
                approvalService.approveCourse(courseId),
                "Đã duyệt khóa học thành công"));
    }

    @Operation(summary = "Admin từ chối khóa học")
    @PostMapping("/admin/courses/{courseId}/reject")
    public ResponseEntity<ApiResponse<CourseResponse>> rejectCourse(
            @PathVariable UUID courseId,
            @RequestParam String reason) {

        return ResponseEntity.ok(ApiResponse.success(
                approvalService.rejectCourse(courseId, reason),
                "Đã từ chối khóa học"));
    }

    @Operation(summary = "Admin ẩn khóa học vi phạm")
    @PostMapping("/admin/courses/{courseId}/hide")
    public ResponseEntity<ApiResponse<CourseResponse>> hideCourse(
            @PathVariable UUID courseId) {

        return ResponseEntity.ok(ApiResponse.success(
                approvalService.hideCourse(courseId), "Đã ẩn khóa học"));
    }

    @Operation(summary = "Admin bỏ ẩn khóa học")
    @PostMapping("/admin/courses/{courseId}/unhide")
    public ResponseEntity<ApiResponse<CourseResponse>> unhideCourse(
            @PathVariable UUID courseId) {

        return ResponseEntity.ok(ApiResponse.success(
                approvalService.unhideCourse(courseId), "Đã bỏ ẩn khóa học"));
    }
}