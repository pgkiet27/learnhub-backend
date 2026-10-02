package com.learnhub.course.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.math.BigDecimal;

@Data
@Builder
public class CouponValidationResponse {
    // Lombok strips the "is" prefix from boolean getters, so the JSON name loses its "is" and no
    // longer matches the Frontend type. Pin the JSON name on the getter, as UserResponse does.
    @Getter(onMethod_ = @JsonProperty("isValid"))
    private boolean isValid;
    private String couponCode;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal discountAmount;
    private BigDecimal originalPrice;
    private BigDecimal finalPrice;
    private String message;
}
