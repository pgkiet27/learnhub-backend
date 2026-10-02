package com.learnhub.enrollment.churn;

import com.learnhub.enrollment.client.dto.UserActivity;
import com.learnhub.enrollment.entity.Enrollment;
import com.learnhub.enrollment.entity.LessonProgress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChurnFeatureCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    private static Enrollment enrollment(Instant lastAccessedAt) {
        return Enrollment.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .courseId(UUID.randomUUID())
                .courseTitle("AWS")
                .progressPercent(new BigDecimal("42.50"))
                .enrolledAt(NOW.minus(Duration.ofDays(30)))
                .lastAccessedAt(lastAccessedAt)
                .build();
    }

    private static LessonProgress progress(int watched, Integer duration, boolean completed,
                                           Instant createdAt, Instant updatedAt, Instant completedAt) {
        return LessonProgress.builder()
                .watchDurationSec(watched)
                .videoDurationSec(duration)
                .isCompleted(completed)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .completedAt(completedAt)
                .build();
    }

    @Test
    @DisplayName("compute - full data - all eight features with expected values")
    void compute_FullData() {
        Instant twoDaysAgo = NOW.minus(Duration.ofDays(2));
        List<LessonProgress> rows = List.of(
                progress(450, 600, false, twoDaysAgo, twoDaysAgo, null),                     // 75%
                progress(600, 600, true, NOW.minus(Duration.ofDays(5)), NOW.minus(Duration.ofDays(1)),
                        NOW.minus(Duration.ofDays(1))));                                       // 100%, took 4 days
        UserActivity activity = new UserActivity(UUID.randomUUID(), "a@b.c", NOW.minus(Duration.ofDays(3)), 2, 4);

        Map<String, Double> f = ChurnFeatureCalculator.compute(enrollment(twoDaysAgo), rows, activity, 3L, 1L, NOW);

        assertThat(f).containsEntry("current_course_progress", 42.5)
                .containsEntry("days_since_last_lesson", 2.0)
                .containsEntry("watch_percentage_last_week", 87.5)
                .containsEntry("days_to_complete_last_lesson", 4.0)
                .containsEntry("days_since_last_login", 3.0)
                .containsEntry("login_frequency_trend", -0.5)
                .containsEntry("quiz_failure_count", 3.0)
                .containsEntry("support_tickets_opened", 1.0)
                .hasSize(8);
    }

    @Test
    @DisplayName("compute - never opened a lesson, no login data - falls back to enrolledAt, omits unknowns")
    void compute_NoActivity() {
        Map<String, Double> f = ChurnFeatureCalculator.compute(enrollment(null), List.of(), null, null, null, NOW);

        assertThat(f).containsEntry("days_since_last_lesson", 30.0)
                .containsEntry("watch_percentage_last_week", 0.0)
                .doesNotContainKeys("days_to_complete_last_lesson", "days_since_last_login", "login_frequency_trend",
                        "quiz_failure_count", "support_tickets_opened");
    }

    @Test
    @DisplayName("watchPercentageLastWeek - only old activity - 0")
    void watchPercentage_OldActivityOnly() {
        Instant tenDaysAgo = NOW.minus(Duration.ofDays(10));
        List<LessonProgress> rows = List.of(progress(600, 600, true, tenDaysAgo, tenDaysAgo, tenDaysAgo));

        assertThat(ChurnFeatureCalculator.watchPercentageLastWeek(rows, NOW)).isZero();
    }

    @Test
    @DisplayName("watchPercentageLastWeek - recent rows without known video length - unknown (null)")
    void watchPercentage_UnknownDuration() {
        Instant yesterday = NOW.minus(Duration.ofDays(1));
        List<LessonProgress> rows = List.of(progress(300, null, false, yesterday, yesterday, null));

        assertThat(ChurnFeatureCalculator.watchPercentageLastWeek(rows, NOW)).isNull();
    }

    @Test
    @DisplayName("loginTrend - growing, shrinking and no activity")
    void loginTrend() {
        assertThat(ChurnFeatureCalculator.loginTrend(4, 0)).isEqualTo(1.0);
        assertThat(ChurnFeatureCalculator.loginTrend(1, 4)).isEqualTo(-0.75);
        assertThat(ChurnFeatureCalculator.loginTrend(3, 3)).isZero();
        assertThat(ChurnFeatureCalculator.loginTrend(0, 0)).isEqualTo(-1.0);
    }
}
