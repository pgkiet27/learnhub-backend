package com.learnhub.payment.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.common.dto.PageResponse;
import com.learnhub.payment.dto.response.CheckoutResponse;
import com.learnhub.payment.dto.response.PaymentHistoryResponse;
import com.learnhub.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Payments", description = "Course payment via Stripe")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Create a Payment Intent to pay for a course")
    @PostMapping("/checkout/{courseId}")
    public ResponseEntity<ApiResponse<CheckoutResponse>> createCheckout(
            @PathVariable UUID courseId,
            @RequestHeader("X-User-Id") UUID userId) {

        CheckoutResponse response = paymentService.createCheckout(userId, courseId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Payment Intent created"));
    }

    @Operation(summary = "View your own transaction history")
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<PageResponse<PaymentHistoryResponse>>> getPaymentHistory(
            @RequestHeader("X-User-Id") UUID userId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                paymentService.getPaymentHistory(userId, pageable), "OK"));
    }
}