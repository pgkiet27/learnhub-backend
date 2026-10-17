package com.learnhub.enrollment.repository;

import com.learnhub.enrollment.AbstractIntegrationTest;
import com.learnhub.enrollment.churn.ChurnSnapshot;
import com.learnhub.enrollment.entity.Enrollment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChurnSnapshotRepository Integration Tests")
@Transactional
class ChurnSnapshotRepositoryIT extends AbstractIntegrationTest {

    private static final Instant SNAPSHOT_AT = Instant.parse("2026-01-05T19:00:00Z");   // 02:00 on 06/01 in Vietnam
    private static final LocalDate SNAPSHOT_DATE = LocalDate.of(2026, 1, 6);
    private static final Instant NOW = SNAPSHOT_AT.plus(Duration.ofDays(14));

    @Autowired
    private ChurnSnapshotRepository snapshotRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    private Enrollment enrollment(Instant lastAccessedAt, Instant completedAt) {
        return enrollment(lastAccessedAt, completedAt, null, null);
    }

    private Enrollment enrollment(Instant lastAccessedAt, Instant completedAt, Instant remindedAt, Instant deliveredAt) {
        return enrollmentRepository.saveAndFlush(Enrollment.builder()
                .userId(UUID.randomUUID()).courseId(UUID.randomUUID()).courseTitle("Course")
                .enrolledAt(SNAPSHOT_AT.minus(Duration.ofDays(30)))
                .lastAccessedAt(lastAccessedAt)
                .isCompleted(completedAt != null).completedAt(completedAt)
                .churnRemindedAt(remindedAt).churnReminderDeliveredAt(deliveredAt)
                .build());
    }

    private static ChurnSnapshot snapshot(Enrollment e, double progress, boolean reminderQueued) {
        return new ChurnSnapshot(e.getId(), e.getUserId(), e.getCourseId(), e.getEnrolledAt(),
                Map.of("current_course_progress", progress), "identity", new BigDecimal("0.8123"), "high",
                "XGBoost", reminderQueued);
    }

    private static Instant afterSnapshot(int days) {
        return SNAPSHOT_AT.plus(Duration.ofDays(days));
    }

    @Test
    @DisplayName("labelDue - 14 days later - labels each enrollment by its activity in the window")
    void labelDue_LabelsByActivityInWindow() {
        Enrollment active = enrollment(afterSnapshot(3), null);
        Enrollment inactive = enrollment(afterSnapshot(-2), null);
        Enrollment completed = enrollment(afterSnapshot(5), afterSnapshot(5));
        Enrollment returnedLate = enrollment(afterSnapshot(18), null);
        Enrollment remindedInactive = enrollment(afterSnapshot(-2), null, SNAPSHOT_AT, SNAPSHOT_AT.plusSeconds(3));
        Enrollment optedOut = enrollment(afterSnapshot(-2), null, SNAPSHOT_AT, null);
        snapshotRepository.saveAll(SNAPSHOT_DATE, SNAPSHOT_AT, List.of(
                snapshot(active, 10, false), snapshot(inactive, 10, false), snapshot(completed, 90, false),
                snapshot(returnedLate, 10, false), snapshot(remindedInactive, 10, true), snapshot(optedOut, 10, true)));

        List<LocalDate> labeled = snapshotRepository.labelDue(NOW, 14);

        assertThat(labeled).hasSize(6).containsOnly(SNAPSHOT_DATE);
        Map<Object, Map<String, Object>> labels = snapshotRepository.findLabels(SNAPSHOT_DATE).stream()
                .collect(Collectors.toMap(r -> r.get("enrollment_id"), r -> r));
        assertThat(labels.get(active.getId()).get("churn")).isEqualTo(0);
        assertThat(labels.get(inactive.getId()).get("churn")).isEqualTo(1);
        assertThat(labels.get(completed.getId()).get("churn")).isEqualTo(0);
        assertThat(labels.get(returnedLate.getId()).get("churn")).isNull();
        assertThat(labels.get(remindedInactive.getId()))
                .containsEntry("churn", 1)
                .containsEntry("reminded_in_window", true);
        assertThat(labels.get(inactive.getId())).containsEntry("reminded_in_window", false);
        // Queued but never delivered (e.g. reminders turned off) does not count as reminded
        assertThat(labels.get(optedOut.getId())).containsEntry("reminded_in_window", false);
    }

    @Test
    @DisplayName("labelDue - window not over yet - leaves the snapshot unlabelled")
    void labelDue_WindowNotOver() {
        Enrollment e = enrollment(afterSnapshot(-2), null);
        snapshotRepository.saveAll(SNAPSHOT_DATE, SNAPSHOT_AT, List.of(snapshot(e, 10, false)));

        assertThat(snapshotRepository.labelDue(afterSnapshot(10), 14)).isEmpty();
        assertThat(snapshotRepository.findLabels(SNAPSHOT_DATE)).isEmpty();
    }

    @Test
    @DisplayName("saveAll - second run on the same date - replaces the row, missing features stay NULL")
    void saveAll_SameDateTwice() {
        Enrollment e = enrollment(afterSnapshot(-2), null);
        snapshotRepository.saveAll(SNAPSHOT_DATE, SNAPSHOT_AT, List.of(snapshot(e, 10, false)));
        snapshotRepository.saveAll(SNAPSHOT_DATE, SNAPSHOT_AT.plus(Duration.ofHours(5)), List.of(snapshot(e, 25, true)));

        List<Map<String, Object>> rows = snapshotRepository.findFeatures(SNAPSHOT_DATE);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst())
                .containsEntry("current_course_progress", 25.0)
                .containsEntry("reminder_queued", true)
                .containsEntry("missing_sources", "identity")
                .containsEntry("days_since_last_login", null);
    }
}
