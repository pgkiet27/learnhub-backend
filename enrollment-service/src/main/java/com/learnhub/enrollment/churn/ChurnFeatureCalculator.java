package com.learnhub.enrollment.churn;

import com.learnhub.enrollment.client.dto.UserActivity;
import com.learnhub.enrollment.entity.Enrollment;
import com.learnhub.enrollment.entity.LessonProgress;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the churn model's input from LearnHub data. Keys must match the feature names the model was
 * trained with (ai-service churn_prediction).
 *
 * A feature is left out when its data is unavailable (no activity recorded yet, or the owning service
 * did not answer); ai-service then fills it with its training mean.
 */
public final class ChurnFeatureCalculator {

    static final Duration WEEK = Duration.ofDays(7);
    private static final double MAX_DAYS = 365;

    private ChurnFeatureCalculator() {
    }

    /**
     * @param quizFailures  failed quiz attempts in this course (assessment-service), null if unknown
     * @param ticketsOpened support tickets opened in the last 30 days (user-service), null if unknown
     */
    public static Map<String, Double> compute(Enrollment enrollment, List<LessonProgress> progress,
                                              UserActivity activity, Long quizFailures, Long ticketsOpened,
                                              Instant now) {
        Map<String, Double> features = new LinkedHashMap<>();

        features.put("current_course_progress", enrollment.getProgressPercent().doubleValue());

        Instant lastLesson = Objects.requireNonNullElse(enrollment.getLastAccessedAt(), enrollment.getEnrolledAt());
        features.put("days_since_last_lesson", daysBetween(lastLesson, now));

        Double watchPercent = watchPercentageLastWeek(progress, now);
        if (watchPercent != null) {
            features.put("watch_percentage_last_week", watchPercent);
        }

        Double daysToComplete = daysToCompleteLastLesson(progress);
        if (daysToComplete != null) {
            features.put("days_to_complete_last_lesson", daysToComplete);
        }

        if (activity != null) {
            features.put("days_since_last_login", daysBetween(activity.lastActiveAt(), now));
            features.put("login_frequency_trend",
                    loginTrend(activity.activeDaysLast14(), activity.activeDaysPrev14()));
        }
        if (quizFailures != null) {
            features.put("quiz_failure_count", quizFailures.doubleValue());
        }
        if (ticketsOpened != null) {
            features.put("support_tickets_opened", ticketsOpened.doubleValue());
        }
        return features;
    }

    /**
     * Average share of each video watched, over lessons touched in the last 7 days.
     * No activity this week = 0. Completed lessons without a video (text/document) count as 100%.
     * Returns null when there was activity but no video length is known (rows from before it was recorded).
     */
    static Double watchPercentageLastWeek(List<LessonProgress> progress, Instant now) {
        Instant weekAgo = now.minus(WEEK);
        List<LessonProgress> recent = progress.stream()
                .filter(p -> p.getUpdatedAt() != null && p.getUpdatedAt().isAfter(weekAgo))
                .toList();
        if (recent.isEmpty()) {
            return 0.0;
        }
        List<Double> percents = recent.stream()
                .map(p -> {
                    Integer duration = p.getVideoDurationSec();
                    if (duration != null && duration > 0) {
                        return Math.min(100.0, p.getWatchDurationSec() * 100.0 / duration);
                    }
                    return p.isCompleted() ? 100.0 : null;
                })
                .filter(Objects::nonNull)
                .toList();
        if (percents.isEmpty()) {
            return null;
        }
        return round(percents.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    static Double daysToCompleteLastLesson(List<LessonProgress> progress) {
        return progress.stream()
                .filter(p -> p.isCompleted() && p.getCompletedAt() != null && p.getCreatedAt() != null)
                .max(Comparator.comparing(LessonProgress::getCompletedAt))
                .map(p -> daysBetween(p.getCreatedAt(), p.getCompletedAt()))
                .orElse(null);
    }

    /**
     * (recent - previous) / max(recent, previous) over two 14-day windows of active days, in [-1, 1].
     * No activity in either window is treated as the strongest decline.
     */
    static double loginTrend(int last14, int prev14) {
        int max = Math.max(last14, prev14);
        if (max == 0) {
            return -1.0;
        }
        return round((double) (last14 - prev14) / max);
    }

    private static double daysBetween(Instant from, Instant to) {
        double days = Duration.between(from, to).toMinutes() / 1440.0;
        return round(Math.min(MAX_DAYS, Math.max(0, days)));
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
