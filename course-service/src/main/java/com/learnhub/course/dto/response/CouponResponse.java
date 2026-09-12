package com.learnhub.course.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CouponResponse {
    private UUID id;
    private String code;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal minOrderValue;
    private Integer maxUses;
    private Integer usedCount;
    private UUID courseId;
    private Instant expiresAt;
    private boolean isActive;
    private Instant createdAt;
}
