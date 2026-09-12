package com.learnhub.course.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CouponValidationResponse {
    private boolean isValid;
    private String couponCode;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal discountAmount;
    private BigDecimal originalPrice;
    private BigDecimal finalPrice;
    private String message;
}
