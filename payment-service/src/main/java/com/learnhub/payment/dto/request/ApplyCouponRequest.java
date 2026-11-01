package com.learnhub.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApplyCouponRequest {

    @NotBlank(message = "Please provide a coupon code")
    @Size(max = 50, message = "Coupon code is too long")
    private String code;
}
