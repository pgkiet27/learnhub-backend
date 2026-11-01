package com.learnhub.course.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.course.dto.response.CouponValidationResponse;
import com.learnhub.course.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Internal endpoints for the Payment Service. Not routed by the API Gateway, so students cannot
 * use up a coupon's quota without paying.
 */
@Tag(name = "Internal", description = "APIs used internally between services")
@RestController
@RequestMapping("/api/v1/internal/coupons")
@RequiredArgsConstructor
public class CouponInternalController {

    private final CouponService couponService;

    @Operation(summary = "Validate a coupon and compute the discounted price")
    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<CouponValidationResponse>> validateCoupon(
            @RequestParam String code,
            @RequestParam UUID courseId,
            @RequestParam BigDecimal originalPrice) {
        return ResponseEntity.ok(ApiResponse.success(
                couponService.validateCoupon(code, courseId, originalPrice), "OK"));
    }

    @Operation(summary = "Count one use of a coupon after a successful payment")
    @PostMapping("/{code}/use")
    public ResponseEntity<ApiResponse<Void>> useCoupon(@PathVariable String code) {
        couponService.incrementUsedCount(code);
        return ResponseEntity.ok(ApiResponse.success(null, "OK"));
    }
}
