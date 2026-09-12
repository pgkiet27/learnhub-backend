package com.learnhub.enrollment.service;

import com.learnhub.common.event.CourseCompletedEvent;
import com.learnhub.common.exception.ResourceNotFoundException;
import com.learnhub.enrollment.config.RabbitMQConfig;
import com.learnhub.enrollment.dto.request.UpdateProgressRequest;
import com.learnhub.enrollment.dto.response.LessonProgressResponse;
import com.learnhub.enrollment.entity.Enrollment;
import com.learnhub.enrollment.entity.LessonProgress;
import com.learnhub.enrollment.repository.EnrollmentRepository;
import com.learnhub.enrollment.repository.LessonProgressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LessonProgressService {

    // A video is considered "watched" once watch_duration reaches >= 90% of its total length —
    // not 100%, since students typically don't watch the last few seconds (credits/outro)
    private static final double COMPLETION_THRESHOLD = 0.9;

    private final LessonProgressRepository progressRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final RabbitTemplate rabbitTemplate;

    /**
     * Updates the viewing progress of a lesson (called periodically from the video player, e.g. every 10 seconds).
     */
    @Transactional
    public LessonProgressResponse updateProgress(UUID userId, UUID courseId, UUID lessonId,
                                                 UpdateProgressRequest request) {
        Enrollment enrollment = findEnrollment(userId, courseId);
        LessonProgress progress = findOrCreateProgress(enrollment, userId, lessonId);

        // Don't let progress regress if the student rewinds to a previously watched section
        if (request.getWatchDurationSec() > progress.getWatchDurationSec()) {
            progress.setWatchDurationSec(request.getWatchDurationSec());
        }
        progress.setLastPositionSec(request.getLastPositionSec());

        boolean justCompleted = false;
        if (!progress.isCompleted()
                && request.getVideoDurationSec() != null
                && request.getVideoDurationSec() > 0
                && progress.getWatchDurationSec() >= request.getVideoDurationSec() * COMPLETION_THRESHOLD) {
            progress.setCompleted(true);
            progress.setCompletedAt(Instant.now());
            justCompleted = true;
        }

        progressRepository.save(progress);
        touchEnrollment(enrollment, justCompleted);

        return toProgressResponse(progress);
    }

    /**
     * Manual completion — used for document/text lessons (no watch %),
     * or when the Frontend wants to let the student click "Complete" regardless of watch progress.
     */
    @Transactional
    public LessonProgressResponse markLessonComplete(UUID userId, UUID courseId, UUID lessonId) {
        Enrollment enrollment = findEnrollment(userId, courseId);
        LessonProgress progress = findOrCreateProgress(enrollment, userId, lessonId);

        boolean justCompleted = false;
        if (!progress.isCompleted()) {
            progress.setCompleted(true);
            progress.setCompletedAt(Instant.now());
            progressRepository.save(progress);
            justCompleted = true;
        }

        touchEnrollment(enrollment, justCompleted);
        return toProgressResponse(progress);
    }

    @Transactional(readOnly = true)
    public LessonProgressResponse getProgress(UUID userId, UUID courseId, UUID lessonId) {
        Enrollment enrollment = findEnrollment(userId, courseId);
        return progressRepository.findByEnrollmentIdAndLessonId(enrollment.getId(), lessonId)
                .map(this::toProgressResponse)
                .orElseGet(() -> LessonProgressResponse.builder()
                        .lessonId(lessonId).isCompleted(false)
                        .watchDurationSec(0).lastPositionSec(0)
                        .build());
    }

    // Private helpers

    private LessonProgress findOrCreateProgress(Enrollment enrollment, UUID userId, UUID lessonId) {
        return progressRepository.findByEnrollmentIdAndLessonId(enrollment.getId(), lessonId)
                .orElseGet(() -> LessonProgress.builder()
                        .enrollment(enrollment)
                        .userId(userId)
                        .lessonId(lessonId)
                        .build());
    }

    private void touchEnrollment(Enrollment enrollment, boolean justCompleted) {
        enrollment.setLastAccessedAt(Instant.now());
        if (justCompleted) {
            recalculateEnrollmentProgress(enrollment);
        }
        enrollmentRepository.save(enrollment);
    }

    /**
     * Recalculates completed_lessons/progress_percent for the whole course, based on
     * total_lessons snapshotted at enrollment time (Part 2) — does not call back to Course Service.
     */
    private void recalculateEnrollmentProgress(Enrollment enrollment) {
        long completed = progressRepository.countByEnrollmentIdAndIsCompletedTrue(enrollment.getId());
        int total = enrollment.getTotalLessons();

        BigDecimal percent = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(completed * 100)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        enrollment.setCompletedLessons((int) completed);
        enrollment.setProgressPercent(percent);

        if (percent.compareTo(BigDecimal.valueOf(100)) >= 0 && !enrollment.isCompleted()) {
            enrollment.setCompleted(true);
            enrollment.setCompletedAt(Instant.now());
            publishCourseCompletedEvent(enrollment);
        }
    }

    private void publishCourseCompletedEvent(Enrollment enrollment) {
        CourseCompletedEvent event = CourseCompletedEvent.builder()
                .enrollmentId(enrollment.getId())
                .userId(enrollment.getUserId())
                .courseId(enrollment.getCourseId())
                .courseTitle(enrollment.getCourseTitle())
                .build();
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME, "course.completed", event);
        log.info("Published CourseCompletedEvent for enrollment: {}", enrollment.getId());
    }

    private Enrollment findEnrollment(UUID userId, UUID courseId) {
        return enrollmentRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ENROLLMENT_NOT_FOUND", "You are not enrolled in this course"));
    }

    public LessonProgressResponse toProgressResponse(LessonProgress p) {
        return LessonProgressResponse.builder()
                .id(p.getId())
                .lessonId(p.getLessonId())
                .isCompleted(p.isCompleted())
                .watchDurationSec(p.getWatchDurationSec())
                .lastPositionSec(p.getLastPositionSec())
                .completedAt(p.getCompletedAt())
                .build();
    }
}