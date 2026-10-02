package com.learnhub.enrollment.churn;

import com.learnhub.common.event.ChurnRiskDetectedEvent;
import com.learnhub.enrollment.config.RabbitMQConfig;
import com.learnhub.enrollment.dto.response.ChurnRunSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChurnPredictionService {

    public static final String ROUTING_KEY_HIGH_RISK = "churn.high_risk";

    private final ChurnScorer churnScorer;
    private final RabbitTemplate rabbitTemplate;

    @Value("${churn.reminder-cooldown-days:7}")
    private int reminderCooldownDays;

    /** Scores every enrollment that is not completed yet and queues reminders for new high-risk ones. */
    public ChurnRunSummary scoreActiveEnrollments() {
        Instant now = Instant.now();
        Map<String, Integer> byRisk = new HashMap<>();
        int scored = 0;
        int reminders = 0;

        int page = 0;
        ChurnScorer.PageOutcome outcome;
        do {
            outcome = churnScorer.scorePage(page++, now, cooldown());
            scored += outcome.scored();
            outcome.byRisk().forEach((risk, count) -> byRisk.merge(risk, count, Integer::sum));
            reminders += publish(outcome.reminders());
        } while (outcome.hasNext());

        ChurnRunSummary summary = new ChurnRunSummary(now, scored, byRisk, reminders);
        log.info("Churn scoring finished: {}", summary);
        return summary;
    }

    /** Instructor-triggered reminders; the same cooldown applies, so nobody is emailed twice in a row. */
    public int remindHighRiskStudents(UUID courseId) {
        return publish(churnScorer.claimCourseReminders(courseId, Instant.now(), cooldown()));
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
