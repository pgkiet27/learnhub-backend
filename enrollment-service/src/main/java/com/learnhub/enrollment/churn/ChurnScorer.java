package com.learnhub.enrollment.churn;

import com.learnhub.common.event.ChurnRiskDetectedEvent;
import com.learnhub.enrollment.client.AiServiceClient;
import com.learnhub.enrollment.client.AssessmentServiceClient;
import com.learnhub.enrollment.client.AssessmentServiceClient.UserCourse;
import com.learnhub.enrollment.client.IdentityServiceClient;
import com.learnhub.enrollment.client.UserServiceClient;
import com.learnhub.enrollment.client.dto.ChurnBatchRequest;
import com.learnhub.enrollment.client.dto.ChurnBatchResponse;
import com.learnhub.enrollment.client.dto.QuizFailureCount;
import com.learnhub.enrollment.client.dto.UserActivity;
import com.learnhub.enrollment.entity.Enrollment;
import com.learnhub.enrollment.entity.LessonProgress;
import com.learnhub.enrollment.repository.EnrollmentRepository;
import com.learnhub.enrollment.repository.LessonProgressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Transactional steps of the churn job. Kept apart from ChurnPredictionService so that reminder
 * events are published only after each transaction has committed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChurnScorer {

    static final int BATCH_SIZE = 200;
    static final String HIGH_RISK = "high";

    private final EnrollmentRepository enrollmentRepository;
    private final LessonProgressRepository progressRepository;
    private final IdentityServiceClient identityServiceClient;
    private final AiServiceClient aiServiceClient;
    private final AssessmentServiceClient assessmentServiceClient;
    private final UserServiceClient userServiceClient;

    public record PageOutcome(int scored, Map<String, Integer> byRisk,
                              List<ChurnRiskDetectedEvent> reminders, boolean hasNext) {
    }

    @Transactional
    public PageOutcome scorePage(int page, Instant now, Duration reminderCooldown) {
        // Ordered by id so pages stay stable while rows are updated
        Page<Enrollment> batch = enrollmentRepository.findByIsCompletedFalse(
                PageRequest.of(page, BATCH_SIZE, Sort.by("id")));
        List<Enrollment> enrollments = batch.getContent();
        if (enrollments.isEmpty()) {
            return new PageOutcome(0, Map.of(), List.of(), false);
        }

        Map<UUID, List<LessonProgress>> progressByEnrollment = progressRepository
                .findByEnrollmentIdIn(enrollments.stream().map(Enrollment::getId).toList()).stream()
                .collect(Collectors.groupingBy(p -> p.getEnrollment().getId()));
        Set<UUID> userIds = enrollments.stream().map(Enrollment::getUserId).collect(Collectors.toSet());
        // Each source is optional: if a service is down, its features are left to the model defaults
        Map<UUID, UserActivity> activity = fetchOrEmpty("identity-service",
                () -> identityServiceClient.getActivity(userIds));
        Map<UUID, Long> tickets = fetchOrEmpty("user-service",
                () -> userServiceClient.getSupportTicketCounts(userIds));
        Map<UserCourse, Long> quizFailures = fetchOrEmpty("assessment-service",
                () -> assessmentServiceClient.getFailureCounts(enrollments.stream()
                                .map(e -> new UserCourse(e.getUserId(), e.getCourseId())).toList())
                        .stream()
                        .collect(Collectors.toMap(c -> new UserCourse(c.userId(), c.courseId()),
                                QuizFailureCount::failedAttempts, Long::sum)));

        List<ChurnBatchRequest.Item> items = enrollments.stream()
                .map(e -> new ChurnBatchRequest.Item(e.getId().toString(), ChurnFeatureCalculator.compute(
                        e, progressByEnrollment.getOrDefault(e.getId(), List.of()), activity.get(e.getUserId()),
                        quizFailures.get(new UserCourse(e.getUserId(), e.getCourseId())), tickets.get(e.getUserId()),
                        now)))
                .toList();
        if (log.isDebugEnabled()) {
            items.forEach(i -> log.debug("Churn features for enrollment {}: {}", i.id(), i.features()));
        }
        Map<String, ChurnBatchResponse.Result> results = aiServiceClient.predictChurn(new ChurnBatchRequest(items))
                .results().stream()
                .collect(Collectors.toMap(ChurnBatchResponse.Result::id, Function.identity()));

        Map<String, Integer> byRisk = new HashMap<>();
        List<ChurnRiskDetectedEvent> reminders = new ArrayList<>();
        int scored = 0;
        for (Enrollment e : enrollments) {
            ChurnBatchResponse.Result r = results.get(e.getId().toString());
            if (r == null) {
                continue;
            }
            e.setChurnScore(r.churnScore().setScale(4, RoundingMode.HALF_UP));
            e.setChurnRiskLevel(r.riskLevel());
            e.setChurnPredictedAt(now);
            byRisk.merge(r.riskLevel(), 1, Integer::sum);
            scored++;

            if (HIGH_RISK.equals(r.riskLevel()) && reminderDue(e, now, reminderCooldown)) {
                e.setChurnRemindedAt(now);
                reminders.add(toEvent(e));
            }
        }
        enrollmentRepository.saveAll(enrollments);
        return new PageOutcome(scored, byRisk, reminders, batch.hasNext());
    }

    /** Marks the course's high-risk students (outside the cooldown) as reminded and returns their events. */
    @Transactional
    public List<ChurnRiskDetectedEvent> claimCourseReminders(UUID courseId, Instant now, Duration reminderCooldown) {
        List<ChurnRiskDetectedEvent> reminders = new ArrayList<>();
        for (Enrollment e : enrollmentRepository.findByCourseIdAndIsCompletedFalseAndChurnRiskLevel(courseId, HIGH_RISK)) {
            if (reminderDue(e, now, reminderCooldown)) {
                e.setChurnRemindedAt(now);
                reminders.add(toEvent(e));
            }
        }
        return reminders;
    }

    private static <K, V> Map<K, V> fetchOrEmpty(String service, Supplier<Map<K, V>> call) {
        try {
            return call.get();
        } catch (Exception ex) {
            // Score without these features rather than skipping the whole batch
            log.warn("Could not load churn features from {}: {}", service, ex.getMessage());
            return Map.of();
        }
    }

    private static boolean reminderDue(Enrollment e, Instant now, Duration cooldown) {
        return e.getChurnRemindedAt() == null || e.getChurnRemindedAt().isBefore(now.minus(cooldown));
    }

    private static ChurnRiskDetectedEvent toEvent(Enrollment e) {
        return ChurnRiskDetectedEvent.builder()
                .enrollmentId(e.getId())
                .userId(e.getUserId())
                .courseId(e.getCourseId())
                .courseTitle(e.getCourseTitle())
                .progressPercent(e.getProgressPercent())
                .churnScore(e.getChurnScore())
                .lastAccessedAt(e.getLastAccessedAt())
                .build();
    }
}
