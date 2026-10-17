package com.learnhub.course.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
public class CreateCouponRequest {
    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9]{4,20}$",
            message = "Mã coupon chỉ gồm chữ và số, 4-20 ký tự")
    private String code;

    @NotBlank
    private String discountType;      // percent | fixed

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal discountValue;

    private BigDecimal minOrderValue;
    private Integer maxUses;
    private UUID courseId;
    private Instant expiresAt;
}
