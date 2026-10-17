package com.learnhub.course.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.common.dto.PageResponse;
import com.learnhub.course.dto.request.CreateQaAnswerRequest;
import com.learnhub.course.dto.request.CreateQaQuestionRequest;
import com.learnhub.course.dto.response.QaAnswerResponse;
import com.learnhub.course.dto.response.QaQuestionResponse;
import com.learnhub.course.service.QaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Q&A", description = "Hỏi đáp trong bài học")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class QaController {

    private final QaService qaService;

    @Operation(summary = "Lấy danh sách câu hỏi của bài học")
    @GetMapping("/lessons/{lessonId}/questions")
    public ResponseEntity<ApiResponse<PageResponse<QaQuestionResponse>>> getQuestions(
            @PathVariable UUID lessonId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                qaService.getQuestionsByLesson(lessonId, PageRequest.of(page, size)), "OK"));
    }

    @Operation(summary = "Đặt câu hỏi")
    @PostMapping("/lessons/{lessonId}/questions")
    public ResponseEntity<ApiResponse<QaQuestionResponse>> createQuestion(
            @PathVariable UUID lessonId,
            @RequestBody @Valid CreateQaQuestionRequest request,
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                qaService.createQuestion(lessonId, request, userId),
                "Đã đặt câu hỏi thành công"));
    }

    @Operation(summary = "Upvote câu hỏi")
    @PostMapping("/questions/{questionId}/upvote")
    public ResponseEntity<ApiResponse<QaQuestionResponse>> upvoteQuestion(
            @PathVariable UUID questionId,
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(
                qaService.upvoteQuestion(questionId, userId), "OK"));
    }

    @Operation(summary = "Mark câu hỏi đã được giải quyết")
    @PostMapping("/questions/{questionId}/resolve")
    public ResponseEntity<ApiResponse<QaQuestionResponse>> resolveQuestion(
            @PathVariable UUID questionId,
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(
                qaService.resolveQuestion(questionId, userId), "OK"));
    }

    @Operation(summary = "Trả lời câu hỏi")
    @PostMapping("/questions/{questionId}/answers")
    public ResponseEntity<ApiResponse<QaAnswerResponse>> createAnswer(
            @PathVariable UUID questionId,
            @RequestBody @Valid CreateQaAnswerRequest request,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "student") String userRole) {
        boolean isInstructor = "instructor".equals(userRole);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                qaService.createAnswer(questionId, request, userId, isInstructor),
                "Đã trả lời câu hỏi"));
    }

    @Operation(summary = "Chấp nhận câu trả lời")
    @PostMapping("/questions/{questionId}/answers/{answerId}/accept")
    public ResponseEntity<ApiResponse<QaAnswerResponse>> acceptAnswer(
            @PathVariable UUID questionId,
            @PathVariable UUID answerId,
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(
                qaService.acceptAnswer(questionId, answerId, userId), "OK"));
    }

    @Operation(summary = "Upvote câu trả lời")
    @PostMapping("/answers/{answerId}/upvote")
    public ResponseEntity<ApiResponse<QaAnswerResponse>> upvoteAnswer(
            @PathVariable UUID answerId) {
        return ResponseEntity.ok(ApiResponse.success(
                qaService.upvoteAnswer(answerId), "OK"));
    }
}