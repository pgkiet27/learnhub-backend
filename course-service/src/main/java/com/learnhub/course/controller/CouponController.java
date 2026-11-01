package com.learnhub.course.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.course.dto.request.CreateCouponRequest;
import com.learnhub.course.dto.response.CouponResponse;
import com.learnhub.course.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Coupons", description = "Quản lý mã giảm giá")
@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @Operation(summary = "Instructor tạo coupon")
    @PostMapping
    public ResponseEntity<ApiResponse<CouponResponse>> createCoupon(
            @RequestBody @Valid CreateCouponRequest request,
            @RequestHeader("X-User-Id") UUID instructorId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                couponService.createCoupon(request, instructorId),
                "Tạo coupon thành công"));
    }

    @Operation(summary = "Deactivate coupon")
    @DeleteMapping("/{couponId}")
    public ResponseEntity<ApiResponse<Void>> deactivateCoupon(
            @PathVariable UUID couponId,
            @RequestHeader("X-User-Id") UUID instructorId) {
        couponService.deactivateCoupon(couponId, instructorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã deactivate coupon"));
    }
}