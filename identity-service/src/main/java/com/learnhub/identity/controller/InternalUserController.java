package com.learnhub.identity.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.identity.dto.request.UserIdsRequest;
import com.learnhub.identity.dto.response.UserActivityResponse;
import com.learnhub.identity.service.UserActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Service-to-service only: the API Gateway does not route /api/v1/internal/**.
 */
@Tag(name = "Internal", description = "APIs used internally between services")
@RestController
@RequestMapping("/api/v1/internal/users")
@RequiredArgsConstructor
public class InternalUserController {
    private final UserActivityService userActivityService;

    @Operation(summary = "Email and login activity of the given users (churn features, notifications)")
    @PostMapping("/activity")
    public ResponseEntity<ApiResponse<List<UserActivityResponse>>> getActivity(
            @Valid @RequestBody UserIdsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userActivityService.getActivity(request.getUserIds())));
    }
}
