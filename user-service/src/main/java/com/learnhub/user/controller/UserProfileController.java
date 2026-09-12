package com.learnhub.user.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.user.dto.request.UpdateProfileRequest;
import com.learnhub.user.dto.response.UserProfileResponse;
import com.learnhub.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "User Profile", description = "manage user profile")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserProfileController {
    private final UserProfileService userProfileService;

    /*
     * GET /api/v1/users/me/profile
     * get profile from user is signing.
     *
     * Header: X-User-Id: {userId}  ← by API Gateway transfer after verify JWT
     */
    @Operation(summary = "Get myself profile")
    @GetMapping("/me/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @RequestHeader("X-User-Id")UUID userId
            ) {
        UserProfileResponse profile = userProfileService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(profile, "OK"));
    }

    /*
     * PUT /api/v1/users/me/profile
     * Update profile from user is signing.
     */
    @Operation(summary = "Update myself profile")
    @PutMapping("/me/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody @Valid UpdateProfileRequest request) {

        UserProfileResponse profile = userProfileService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success(profile, "Profile updated successfully"));
    }

    /**
     * GET /api/v1/users/{userId}/profile
     * See others public profile
     */
    @Operation(summary = "See others public profile")
    @GetMapping("/{userId}/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getPublicProfile(
            @PathVariable UUID userId) {

        UserProfileResponse profile = userProfileService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(profile, "OK"));
    }
}
