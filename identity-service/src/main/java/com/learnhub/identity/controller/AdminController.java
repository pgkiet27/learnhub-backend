package com.learnhub.identity.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.identity.dto.response.UserResponse;
import com.learnhub.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Admin", description = "Only for Admins")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AuthService authService;
    /**
     * PUT /api/v1/admin/users/{userId}/role
     * Admin change role to user
     */
    @Operation(summary = "Change role for user (Admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/users/{userId}/role")
    public ResponseEntity<ApiResponse<UserResponse>> updateRole(
            @PathVariable UUID userId,
            @RequestParam String role
    ) {
        UserResponse response = authService.updateUserRole(userId, role);
        return ResponseEntity.ok(ApiResponse.success(response, "Role updated successfully"));
    }
}
