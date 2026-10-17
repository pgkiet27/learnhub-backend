package com.learnhub.enrollment.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.enrollment.dto.request.UpdateProgressRequest;
import com.learnhub.enrollment.dto.response.LessonProgressResponse;
import com.learnhub.enrollment.service.LessonProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Lesson Progress", description = "Per-lesson viewing progress")
@RestController
@RequestMapping("/api/v1/enrollments/{courseId}/lessons/{lessonId}/progress")
@RequiredArgsConstructor
public class LessonProgressController {

    private final LessonProgressService progressService;

    @Operation(summary = "Get the viewing progress of a lesson")
    @GetMapping
    public ResponseEntity<ApiResponse<LessonProgressResponse>> getProgress(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(
                progressService.getProgress(userId, courseId, lessonId), "OK"));
    }

    @Operation(summary = "Update viewing progress (called periodically from the video player)")
    @PutMapping
    public ResponseEntity<ApiResponse<LessonProgressResponse>> updateProgress(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @RequestBody @Valid UpdateProgressRequest request,
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(
                progressService.updateProgress(userId, courseId, lessonId, request), "OK"));
    }

    @Operation(summary = "Manually mark a lesson complete (document/text lessons, or a manual click)")
    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<LessonProgressResponse>> markComplete(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(
                progressService.markLessonComplete(userId, courseId, lessonId), "Marked as completed"));
    }
}