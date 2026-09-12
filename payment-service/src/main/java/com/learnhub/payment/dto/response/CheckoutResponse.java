package com.learnhub.payment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class CheckoutResponse {
    private UUID paymentId;
    private String clientSecret;
    private BigDecimal amount;
    private String currency;
}