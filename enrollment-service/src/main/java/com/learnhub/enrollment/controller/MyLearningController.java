package com.learnhub.enrollment.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.common.dto.PageResponse;
import com.learnhub.enrollment.dto.response.EnrollmentResponse;
import com.learnhub.enrollment.service.MyLearningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "My Learning", description = "List of in-progress / completed courses")
@RestController
@RequestMapping("/api/v1/my-learning")
@RequiredArgsConstructor
public class MyLearningController {

    private final MyLearningService myLearningService;

    @Operation(
            summary = "List of my courses",
            description = "status = in_progress | completed | all (defaults to all)"
    )
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<EnrollmentResponse>>> getMyLearning(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        PageResponse<EnrollmentResponse> result = myLearningService
                .getMyLearning(userId, status, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(result, "OK"));
    }
}