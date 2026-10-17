package com.learnhub.enrollment.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * @param snapshotsSaved rows added to the training dataset; more than scored when ai-service failed
 * @param labeled        older snapshots whose 14-day window ended and got their churn label
 * @param exported       S3 keys written, empty when the export is disabled
 */
public record ChurnRunSummary(Instant ranAt, int scored, Map<String, Integer> byRisk, int remindersQueued,
                              int snapshotsSaved, int labeled, List<String> exported) {
}
