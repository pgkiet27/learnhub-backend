package com.learnhub.payment.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.payment.dto.request.RefundRequest;
import com.learnhub.payment.dto.response.RefundResponse;
import com.learnhub.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Payments - Admin", description = "Process refunds (Admin only)")
@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
public class PaymentAdminController {

    private final PaymentService paymentService;

    @Operation(summary = "Refund a transaction — fully or partially")
    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<ApiResponse<RefundResponse>> refundPayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody RefundRequest request) {

        RefundResponse response = paymentService.refundPayment(paymentId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Refund successful"));
    }
}