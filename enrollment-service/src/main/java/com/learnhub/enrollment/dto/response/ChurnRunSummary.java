package com.learnhub.enrollment.dto.response;

import java.time.Instant;
import java.util.Map;

public record ChurnRunSummary(Instant ranAt, int scored, Map<String, Integer> byRisk, int remindersQueued) {
}
