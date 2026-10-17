package com.learnhub.assessment.controller;

import com.learnhub.assessment.repository.QuizAttemptRepository;
import com.learnhub.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service-to-service only: the API Gateway does not route /api/v1/internal/**.
 */
@Tag(name = "Internal", description = "APIs used internally between services")
@RestController
@RequestMapping("/api/v1/internal/quizzes")
@RequiredArgsConstructor
public class InternalQuizController {

    private final QuizAttemptRepository attemptRepository;

    public record UserCourse(@NotNull UUID userId, @NotNull UUID courseId) {
    }

    public record FailureCountsRequest(@NotNull @Size(max = 1000) @Valid List<UserCourse> items) {
    }

    public record FailureCount(UUID userId, UUID courseId, long failedAttempts) {
    }

    @Operation(summary = "Failed quiz attempts per (user, course) — churn feature quiz_failure_count")
    @PostMapping("/failure-counts")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<FailureCount>>> failureCounts(@Valid @RequestBody FailureCountsRequest request) {
        if (request.items().isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }
        Map<UserCourse, Long> counts = new HashMap<>();
        for (Object[] row : attemptRepository.countFailures(
                request.items().stream().map(UserCourse::userId).distinct().toList(),
                request.items().stream().map(UserCourse::courseId).distinct().toList())) {
            counts.put(new UserCourse((UUID) row[0], (UUID) row[1]), ((Number) row[2]).longValue());
        }
        // Every requested pair is answered, 0 when the student never failed a quiz in that course
        List<FailureCount> result = request.items().stream()
                .map(p -> new FailureCount(p.userId(), p.courseId(), counts.getOrDefault(p, 0L)))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
