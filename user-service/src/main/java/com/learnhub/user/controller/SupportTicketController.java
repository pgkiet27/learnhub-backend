package com.learnhub.user.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.common.dto.PageResponse;
import com.learnhub.user.dto.request.CreateSupportTicketRequest;
import com.learnhub.user.dto.request.UpdateSupportTicketRequest;
import com.learnhub.user.dto.response.SupportTicketResponse;
import com.learnhub.user.entity.SupportTicket;
import com.learnhub.user.service.SupportTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Support Tickets", description = "Help requests from users, handled by admins")
@RestController
@RequiredArgsConstructor
public class SupportTicketController {

    private final SupportTicketService supportTicketService;

    @Operation(summary = "Open a support ticket")
    @PostMapping("/api/v1/users/me/support-tickets")
    public ResponseEntity<ApiResponse<SupportTicketResponse>> create(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateSupportTicketRequest request) {
        return ResponseEntity.ok(ApiResponse.success(supportTicketService.create(userId, request), "Ticket created"));
    }

    @Operation(summary = "My support tickets, newest first")
    @GetMapping("/api/v1/users/me/support-tickets")
    public ResponseEntity<ApiResponse<PageResponse<SupportTicketResponse>>> listMine(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                supportTicketService.listMine(userId, PageRequest.of(page, Math.min(size, 100)))));
    }

    @Operation(summary = "[Admin] All tickets; with a status filter, oldest first")
    @GetMapping("/api/v1/admin/support-tickets")
    public ResponseEntity<ApiResponse<PageResponse<SupportTicketResponse>>> listAll(
            @RequestHeader(value = "X-User-Role", defaultValue = "") String role,
            @RequestParam(required = false) SupportTicket.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                supportTicketService.listAll(role, status, PageRequest.of(page, Math.min(size, 100)))));
    }

    @Operation(summary = "[Admin] Reply to and/or change the status of a ticket")
    @PatchMapping("/api/v1/admin/support-tickets/{ticketId}")
    public ResponseEntity<ApiResponse<SupportTicketResponse>> update(
            @RequestHeader("X-User-Id") UUID adminId,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String role,
            @PathVariable UUID ticketId,
            @Valid @RequestBody UpdateSupportTicketRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                supportTicketService.update(adminId, role, ticketId, request), "Ticket updated"));
    }
}
