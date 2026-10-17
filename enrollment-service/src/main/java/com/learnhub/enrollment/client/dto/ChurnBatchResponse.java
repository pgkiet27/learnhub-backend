package com.learnhub.enrollment.client.dto;

import java.math.BigDecimal;
import java.util.List;

public record ChurnBatchResponse(String modelUsed, List<Result> results) {

    public record Result(
            String id,
            BigDecimal churnScore,
            boolean churnLabel,
            String riskLevel,
            List<String> missingFeatures) {
    }
}
