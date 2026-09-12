package com.learnhub.course.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.course.dto.request.CreateLessonRequest;
import com.learnhub.course.dto.response.LessonResponse;
import com.learnhub.course.service.LessonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Lessons", description = "Quản lý bài học")
@RestController
@RequestMapping("/api/v1/courses/{courseId}/sections/{sectionId}/lessons")
@RequiredArgsConstructor
public class LessonController {

    private final LessonService lessonService;

    @Operation(summary = "Tạo bài học mới")
    @PostMapping
    public ResponseEntity<ApiResponse<LessonResponse>> createLesson(
            @PathVariable UUID courseId,
            @PathVariable UUID sectionId,
            @RequestBody @Valid CreateLessonRequest request,
            @RequestHeader("X-User-Id") UUID instructorId) {

        LessonResponse lesson = lessonService.createLesson(
                courseId, sectionId, request, instructorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(lesson, "Tạo bài học thành công"));
    }

    @Operation(summary = "Cập nhật media URL sau khi upload xong lên S3")
    @PatchMapping("/{lessonId}/media")
    public ResponseEntity<ApiResponse<LessonResponse>> updateLessonMedia(
            @PathVariable UUID courseId,
            @PathVariable UUID sectionId,
            @PathVariable UUID lessonId,
            @RequestParam String videoUrl,
            @RequestParam(required = false) Integer videoDuration,
            @RequestHeader("X-User-Id") UUID instructorId) {

        LessonResponse lesson = lessonService.updateLessonMediaUrl(
                lessonId, videoUrl, videoDuration, instructorId);
        return ResponseEntity.ok(ApiResponse.success(lesson, "Cập nhật media thành công"));
    }

    @Operation(summary = "Publish bài học")
    @PostMapping("/{lessonId}/publish")
    public ResponseEntity<ApiResponse<LessonResponse>> publishLesson(
            @PathVariable UUID courseId,
            @PathVariable UUID sectionId,
            @PathVariable UUID lessonId,
            @RequestHeader("X-User-Id") UUID instructorId) {

        LessonResponse lesson = lessonService.publishLesson(lessonId, instructorId);
        return ResponseEntity.ok(ApiResponse.success(lesson, "Publish bài học thành công"));
    }
}