package com.learnhub.enrollment.dto.response;

import com.learnhub.common.dto.PageResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CourseStudentsResponse(
        UUID courseId,
        String courseTitle,
        Stats stats,
        PageResponse<Student> students) {

    public record Stats(
            long total,
            long completed,
            long notStarted,
            long highRisk,
            long mediumRisk,
            Instant lastPredictedAt) {
    }

    public record Student(
            UUID userId,
            String fullName,
            String avatarUrl,
            BigDecimal progressPercent,
            Integer completedLessons,
            Integer totalLessons,
            boolean completed,
            Instant enrolledAt,
            Instant lastAccessedAt,
            BigDecimal churnScore,
            String churnRiskLevel,
            Instant churnPredictedAt,
            Instant churnRemindedAt) {
    }
}
