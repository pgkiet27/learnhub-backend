package com.learnhub.user.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.common.dto.PageResponse;
import com.learnhub.user.dto.response.WishlistResponse;
import com.learnhub.user.service.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Wishlist", description = "Manage wishlist courses list")
@RestController
@RequestMapping("/api/v1/users/me/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @Operation(summary = "Get wishlist courses")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<WishlistResponse>>> getWishlist(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {

        PageResponse<WishlistResponse> wishlist = wishlistService
                .getWishlist(userId, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(wishlist, "OK"));
    }

    @Operation(summary = "Add course into wishlist")
    @PostMapping("/{courseId}")
    public ResponseEntity<ApiResponse<Void>> addToWishlist(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID courseId) {

        wishlistService.addToWishlist(userId, courseId);
        return ResponseEntity.ok(ApiResponse.success(null, "Added wishlist"));
    }

    @Operation(summary = "Delete course from wishlist")
    @DeleteMapping("/{courseId}")
    public ResponseEntity<ApiResponse<Void>> removeFromWishlist(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID courseId) {

        wishlistService.removeFromWishlist(userId, courseId);
        return ResponseEntity.ok(ApiResponse.success(null, "Deleted from wishlist"));
    }
}