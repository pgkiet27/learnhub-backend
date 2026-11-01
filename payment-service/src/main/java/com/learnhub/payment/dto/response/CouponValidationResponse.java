package com.learnhub.payment.dto.response;

import java.math.BigDecimal;

public record CouponValidationResponse(
        boolean isValid,
        String couponCode,
        BigDecimal discountAmount,
        BigDecimal finalPrice,
        String message) {
}
