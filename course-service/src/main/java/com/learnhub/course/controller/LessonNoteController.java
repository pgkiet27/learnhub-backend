package com.learnhub.course.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.course.dto.request.CreateLessonNoteRequest;
import com.learnhub.course.dto.response.LessonNoteResponse;
import com.learnhub.course.service.LessonNoteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Lesson Notes", description = "Ghi chú bài học")
@RestController
@RequestMapping("/api/v1/lessons/{lessonId}/notes")
@RequiredArgsConstructor
public class LessonNoteController {

    private final LessonNoteService noteService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LessonNoteResponse>>> getNotes(
            @PathVariable UUID lessonId,
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(
                noteService.getNotesByLesson(lessonId, userId), "OK"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LessonNoteResponse>> createNote(
            @PathVariable UUID lessonId,
            @RequestBody @Valid CreateLessonNoteRequest request,
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                noteService.createNote(lessonId, request, userId),
                "Tạo ghi chú thành công"));
    }

    @PutMapping("/{noteId}")
    public ResponseEntity<ApiResponse<LessonNoteResponse>> updateNote(
            @PathVariable UUID lessonId,
            @PathVariable UUID noteId,
            @RequestBody @Valid CreateLessonNoteRequest request,
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(
                noteService.updateNote(noteId, request, userId), "Cập nhật thành công"));
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<ApiResponse<Void>> deleteNote(
            @PathVariable UUID lessonId,
            @PathVariable UUID noteId,
            @RequestHeader("X-User-Id") UUID userId) {
        noteService.deleteNote(noteId, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa ghi chú thành công"));
    }
}
