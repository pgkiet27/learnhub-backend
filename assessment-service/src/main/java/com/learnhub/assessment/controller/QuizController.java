package com.learnhub.assessment.controller;

import com.learnhub.assessment.dto.request.QuizRequest;
import com.learnhub.assessment.dto.request.SubmitAttemptRequest;
import com.learnhub.assessment.dto.response.QuizResponses.AttemptResult;
import com.learnhub.assessment.dto.response.QuizResponses.QuizDetail;
import com.learnhub.assessment.dto.response.QuizResponses.QuizSummary;
import com.learnhub.assessment.dto.response.QuizResponses.QuizToTake;
import com.learnhub.assessment.dto.response.QuizResponses.StudentQuizItem;
import com.learnhub.assessment.service.QuizManagementService;
import com.learnhub.assessment.service.QuizTakingService;
import com.learnhub.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Quizzes", description = "Quiz authoring (instructor) and taking (student)")
@RestController
@RequestMapping("/api/v1/quizzes")
@RequiredArgsConstructor
public class QuizController {

    private static final String ROLE_HEADER = "X-User-Role";

    private final QuizManagementService managementService;
    private final QuizTakingService takingService;

    // Instructor

    @Operation(summary = "[Instructor] All quizzes of a course, with attempt stats")
    @GetMapping("/manage/courses/{courseId}")
    public ResponseEntity<ApiResponse<List<QuizSummary>>> listForInstructor(
            @PathVariable UUID courseId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = ROLE_HEADER, defaultValue = "student") String role) {
        return ResponseEntity.ok(ApiResponse.success(managementService.listForCourse(courseId, userId, role)));
    }

    @Operation(summary = "[Instructor] Quiz with correct answers, for editing")
    @GetMapping("/manage/{quizId}")
    public ResponseEntity<ApiResponse<QuizDetail>> getForEditing(
            @PathVariable UUID quizId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = ROLE_HEADER, defaultValue = "student") String role) {
        return ResponseEntity.ok(ApiResponse.success(managementService.get(quizId, userId, role)));
    }

    @Operation(summary = "[Instructor] Create a quiz (unpublished)")
    @PostMapping("/manage")
    public ResponseEntity<ApiResponse<QuizDetail>> create(
            @Valid @RequestBody QuizRequest request,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = ROLE_HEADER, defaultValue = "student") String role) {
        return ResponseEntity.ok(ApiResponse.success(managementService.create(request, userId, role), "Quiz created"));
    }

    @Operation(summary = "[Instructor] Replace a quiz's settings and questions")
    @PutMapping("/manage/{quizId}")
    public ResponseEntity<ApiResponse<QuizDetail>> update(
            @PathVariable UUID quizId,
            @Valid @RequestBody QuizRequest request,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = ROLE_HEADER, defaultValue = "student") String role) {
        return ResponseEntity.ok(ApiResponse.success(managementService.update(quizId, request, userId, role), "Quiz updated"));
    }

    @Operation(summary = "[Instructor] Publish or hide a quiz")
    @PatchMapping("/manage/{quizId}/publish")
    public ResponseEntity<ApiResponse<QuizDetail>> setPublished(
            @PathVariable UUID quizId,
            @RequestParam boolean published,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = ROLE_HEADER, defaultValue = "student") String role) {
        return ResponseEntity.ok(ApiResponse.success(managementService.setPublished(quizId, published, userId, role)));
    }

    @Operation(summary = "[Instructor] Delete a quiz nobody has taken yet")
    @DeleteMapping("/manage/{quizId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID quizId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = ROLE_HEADER, defaultValue = "student") String role) {
        managementService.delete(quizId, userId, role);
        return ResponseEntity.ok(ApiResponse.success(null, "Quiz deleted"));
    }

    // Student

    @Operation(summary = "Published quizzes of an enrolled course, with my progress")
    @GetMapping("/courses/{courseId}")
    public ResponseEntity<ApiResponse<List<StudentQuizItem>>> listForStudent(
            @PathVariable UUID courseId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = ROLE_HEADER, defaultValue = "student") String role) {
        return ResponseEntity.ok(ApiResponse.success(takingService.listForCourse(courseId, userId, role)));
    }

    @Operation(summary = "Quiz to take (without the answers)")
    @GetMapping("/{quizId}")
    public ResponseEntity<ApiResponse<QuizToTake>> getForTaking(
            @PathVariable UUID quizId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = ROLE_HEADER, defaultValue = "student") String role) {
        return ResponseEntity.ok(ApiResponse.success(takingService.getForTaking(quizId, userId, role)));
    }

    @Operation(summary = "Submit answers; graded immediately")
    @PostMapping("/{quizId}/attempts")
    public ResponseEntity<ApiResponse<AttemptResult>> submit(
            @PathVariable UUID quizId,
            @Valid @RequestBody SubmitAttemptRequest request,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = ROLE_HEADER, defaultValue = "student") String role) {
        return ResponseEntity.ok(ApiResponse.success(takingService.submit(quizId, userId, role, request), "Submitted"));
    }

    @Operation(summary = "Result of one of my attempts")
    @GetMapping("/attempts/{attemptId}")
    public ResponseEntity<ApiResponse<AttemptResult>> getResult(
            @PathVariable UUID attemptId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = ROLE_HEADER, defaultValue = "student") String role) {
        return ResponseEntity.ok(ApiResponse.success(takingService.getResult(attemptId, userId, role)));
    }
}
