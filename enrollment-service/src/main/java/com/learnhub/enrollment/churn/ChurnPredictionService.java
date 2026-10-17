package com.learnhub.enrollment.churn;

import com.learnhub.common.event.ChurnRiskDetectedEvent;
import com.learnhub.enrollment.config.RabbitMQConfig;
import com.learnhub.enrollment.dto.response.ChurnRunSummary;
import com.learnhub.enrollment.repository.ChurnSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChurnPredictionService {

    public static final String ROUTING_KEY_HIGH_RISK = "churn.high_risk";

    private final ChurnScorer churnScorer;
    private final RabbitTemplate rabbitTemplate;
    private final ChurnSnapshotRepository snapshotRepository;
    private final ChurnDatasetExporter datasetExporter;

    @Value("${churn.reminder-cooldown-days:7}")
    private int reminderCooldownDays;

    @Value("${churn.dataset.label-horizon-days:14}")
    private int labelHorizonDays;

    /**
     * Scores every enrollment that is not completed yet and queues reminders for new high-risk ones,
     * then labels snapshots whose window has ended and exports the dataset.
     */
    public ChurnRunSummary scoreActiveEnrollments() {
        Instant now = Instant.now();
        Map<String, Integer> byRisk = new HashMap<>();
        int scored = 0;
        int snapshots = 0;
        int reminders = 0;

        int page = 0;
        ChurnScorer.PageOutcome outcome;
        do {
            outcome = churnScorer.scorePage(page++, now, cooldown());
            scored += outcome.scored();
            snapshots += outcome.snapshots();
            outcome.byRisk().forEach((risk, count) -> byRisk.merge(risk, count, Integer::sum));
            reminders += publish(outcome.reminders());
        } while (outcome.hasNext());

        List<LocalDate> labeled = labelDueSnapshots(now);
        List<String> exported = datasetExporter.export(ChurnSnapshot.dateOf(now), new TreeSet<>(labeled));

        ChurnRunSummary summary = new ChurnRunSummary(now, scored, byRisk, reminders, snapshots, labeled.size(), exported);
        log.info("Churn scoring finished: {}", summary);
        return summary;
    }

    /** Re-uploads one date's features and labels, e.g. after an S3 outage. */
    public List<String> exportDataset(LocalDate date) {
        return datasetExporter.export(date, List.of(date));
    }

    /** Instructor-triggered reminders; the same cooldown applies, so nobody is emailed twice in a row. */
    public int remindHighRiskStudents(UUID courseId) {
        return publish(churnScorer.claimCourseReminders(courseId, Instant.now(), cooldown()));
    }

    private List<LocalDate> labelDueSnapshots(Instant now) {
        try {
            return snapshotRepository.labelDue(now, labelHorizonDays);
        } catch (Exception ex) {
            // Unlabelled rows stay due and are picked up by the next run
            log.error("Could not label churn snapshots", ex);
            return List.of();
        }
    }

    private int publish(List<ChurnRiskDetectedEvent> events) {
        int sent = 0;
        for (ChurnRiskDetectedEvent event : events) {
            try {
                rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, ROUTING_KEY_HIGH_RISK, event);
                sent++;
            } catch (AmqpException ex) {
                log.error("Could not publish churn reminder for enrollment {}: {}",
                        event.getEnrollmentId(), ex.getMessage());
            }
        }
        return sent;
    }

    private Duration cooldown() {
        return Duration.ofDays(reminderCooldownDays);
    }
}
