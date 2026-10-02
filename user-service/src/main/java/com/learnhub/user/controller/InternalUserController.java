package com.learnhub.user.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.user.dto.request.UserIdsRequest;
import com.learnhub.user.dto.response.UserSummaryResponse;
import com.learnhub.user.service.SupportTicketService;
import com.learnhub.user.service.UserSummaryService;
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
import java.util.UUID;

/**
 * Service-to-service only: the API Gateway does not route /api/v1/internal/**.
 */
@Tag(name = "Internal", description = "APIs used internally between services")
@RestController
@RequestMapping("/api/v1/internal/users")
@RequiredArgsConstructor
public class InternalUserController {
    private final UserSummaryService userSummaryService;
    private final SupportTicketService supportTicketService;

    public record SupportTicketCount(UUID userId, long ticketsLast30Days) {
    }

    @Operation(summary = "Display name, avatar and reminder preference of the given users")
    @PostMapping("/summaries")
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> getSummaries(
            @Valid @RequestBody UserIdsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userSummaryService.getSummaries(request.getUserIds())));
    }

    @Operation(summary = "Support tickets opened in the last 30 days per user (churn feature)")
    @PostMapping("/support-ticket-counts")
    public ResponseEntity<ApiResponse<List<SupportTicketCount>>> getSupportTicketCounts(
            @Valid @RequestBody UserIdsRequest request) {
        List<SupportTicketCount> counts = supportTicketService.countRecent(request.getUserIds()).entrySet().stream()
                .map(e -> new SupportTicketCount(e.getKey(), e.getValue()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(counts));
    }
}
