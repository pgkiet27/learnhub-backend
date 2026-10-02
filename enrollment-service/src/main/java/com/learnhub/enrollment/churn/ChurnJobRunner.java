package com.learnhub.enrollment.churn;

import com.learnhub.enrollment.dto.response.ChurnRunSummary;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Runs churn scoring under a ShedLock lock so that, with several enrollment-service replicas, only one
 * scores at a time — both for the nightly schedule and for manual runs.
 */
@Component
@RequiredArgsConstructor
public class ChurnJobRunner {

    static final String LOCK_NAME = "churn-scoring";
    // Released when the run ends; lockAtMostFor only matters if the replica dies mid-run
    private static final Duration LOCK_AT_MOST = Duration.ofMinutes(30);
    // Keeps replicas whose schedules fire a few seconds apart from running it twice
    private static final Duration LOCK_AT_LEAST = Duration.ofSeconds(30);

    private final LockingTaskExecutor lockingTaskExecutor;
    private final ChurnPredictionService churnPredictionService;

    /** Empty when another replica holds the lock. */
    public Optional<ChurnRunSummary> runIfNotRunning() {
        LockConfiguration lock = new LockConfiguration(Instant.now(), LOCK_NAME, LOCK_AT_MOST, LOCK_AT_LEAST);
        try {
            LockingTaskExecutor.TaskResult<ChurnRunSummary> result =
                    lockingTaskExecutor.executeWithLock(churnPredictionService::scoreActiveEnrollments, lock);
            return result.wasExecuted() ? Optional.ofNullable(result.getResult()) : Optional.empty();
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("Churn scoring failed", t);
        }
    }
}
