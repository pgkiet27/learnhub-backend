package com.learnhub.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// event when payment is successful, used to trigger other services to update data
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSuccessEvent {
    private UUID transactionId;
    private UUID userId;
    private UUID courseId;
    private BigDecimal amount;
    private String currency;

    @Builder.Default
    private Instant occurredAt = Instant.now();
}
