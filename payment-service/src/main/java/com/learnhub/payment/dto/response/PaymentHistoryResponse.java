package com.learnhub.payment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PaymentHistoryResponse {
    private UUID id;
    private UUID courseId;
    private String courseTitle;
    private String courseThumbnailUrl;
    private BigDecimal amount;
    private String currency;
    private String status;
    private Instant paidAt;
    private Instant createdAt;
    private List<RefundResponse> refunds;
}