package com.learnhub.enrollment.churn;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "churn.job.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ChurnPredictionJob {

    private final ChurnJobRunner churnJobRunner;

    @Scheduled(cron = "${churn.job.cron:0 0 2 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        try {
            if (churnJobRunner.runIfNotRunning().isEmpty()) {
                log.info("Churn scoring skipped: another replica holds the lock");
            }
        } catch (Exception ex) {
            log.error("Churn scoring job failed", ex);
        }
    }
}
