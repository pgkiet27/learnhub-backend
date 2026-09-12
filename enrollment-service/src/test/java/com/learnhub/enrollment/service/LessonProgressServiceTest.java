package com.learnhub.enrollment.service;

import com.learnhub.enrollment.dto.request.UpdateProgressRequest;
import com.learnhub.enrollment.entity.Enrollment;
import com.learnhub.enrollment.entity.LessonProgress;
import com.learnhub.enrollment.repository.EnrollmentRepository;
import com.learnhub.enrollment.repository.LessonProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LessonProgressService Unit Tests")
class LessonProgressServiceTest {

    @Mock
    private LessonProgressRepository progressRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private LessonProgressService progressService;

    private UUID userId, courseId, lessonId, enrollmentId;
    private Enrollment enrollment;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        courseId = UUID.randomUUID();
        lessonId = UUID.randomUUID();
        enrollmentId = UUID.randomUUID();

        enrollment = Enrollment.builder()
                .id(enrollmentId).userId(userId).courseId(courseId)
                .courseTitle("Java Basics").totalLessons(2)
                .completedLessons(0).build();
    }

    @Test
    @DisplayName("Video watch below 90% — not yet complete")
    void updateProgress_BelowThreshold_ShouldNotComplete() {
        given(enrollmentRepository.findByUserIdAndCourseId(userId, courseId))
                .willReturn(Optional.of(enrollment));
        given(progressRepository.findByEnrollmentIdAndLessonId(enrollmentId, lessonId))
                .willReturn(Optional.empty());
        given(progressRepository.save(any(LessonProgress.class)))
                .willAnswer(inv -> inv.getArgument(0));

        UpdateProgressRequest request = new UpdateProgressRequest();
        request.setWatchDurationSec(600);
        request.setLastPositionSec(600);
        request.setVideoDurationSec(800);   // 600/800 = 75% < 90%

        var response = progressService.updateProgress(userId, courseId, lessonId, request);

        assertThat(response.isCompleted()).isFalse();
        // Not yet complete → recalculate must NOT be called, enrollment isn't saved
        then(enrollmentRepository).should(times(1)).save(enrollment);
        assertThat(enrollment.getCompletedLessons()).isZero();
    }

    @Test
    @DisplayName("Video watch reaches the 90% threshold — auto-completes and recalculates progress")
    void updateProgress_AboveThreshold_ShouldAutoComplete() {
        given(enrollmentRepository.findByUserIdAndCourseId(userId, courseId))
                .willReturn(Optional.of(enrollment));
        given(progressRepository.findByEnrollmentIdAndLessonId(enrollmentId, lessonId))
                .willReturn(Optional.empty());
        given(progressRepository.save(any(LessonProgress.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(progressRepository.countByEnrollmentIdAndIsCompletedTrue(enrollmentId))
                .willReturn(1L);   // 1/2 lessons completed

        UpdateProgressRequest request = new UpdateProgressRequest();
        request.setWatchDurationSec(750);
        request.setLastPositionSec(750);
        request.setVideoDurationSec(800);   // 750/800 = 93.75% >= 90%

        var response = progressService.updateProgress(userId, courseId, lessonId, request);

        assertThat(response.isCompleted()).isTrue();
        assertThat(enrollment.getCompletedLessons()).isEqualTo(1);
        assertThat(enrollment.getProgressPercent()).isEqualByComparingTo("50.00");
        assertThat(enrollment.isCompleted()).isFalse();   // only 1/2 so far, whole course not done yet
    }

    @Test
    @DisplayName("Complete the last lesson — enrollment completes + CourseCompletedEvent is published")
    void updateProgress_LastLesson_ShouldCompleteEnrollmentAndPublishEvent() {
        given(enrollmentRepository.findByUserIdAndCourseId(userId, courseId))
                .willReturn(Optional.of(enrollment));
        given(progressRepository.findByEnrollmentIdAndLessonId(enrollmentId, lessonId))
                .willReturn(Optional.empty());
        given(progressRepository.save(any(LessonProgress.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(progressRepository.countByEnrollmentIdAndIsCompletedTrue(enrollmentId))
                .willReturn(2L);   // 2/2 lessons completed — whole course done

        var response = progressService.markLessonComplete(userId, courseId, lessonId);

        assertThat(response.isCompleted()).isTrue();
        assertThat(enrollment.isCompleted()).isTrue();
        assertThat(enrollment.getCompletedAt()).isNotNull();
        then(rabbitTemplate).should(times(1))
                .convertAndSend(anyString(), eq("course.completed"), any(Object.class));
    }

    @Test
    @DisplayName("watchDurationSec does not decrease when a smaller value than the saved one is sent")
    void updateProgress_LowerWatchDuration_ShouldNotRegress() {
        LessonProgress existing = LessonProgress.builder()
                .id(UUID.randomUUID()).enrollment(enrollment).userId(userId).lessonId(lessonId)
                .watchDurationSec(700).lastPositionSec(700).isCompleted(false)
                .build();

        given(enrollmentRepository.findByUserIdAndCourseId(userId, courseId))
                .willReturn(Optional.of(enrollment));
        given(progressRepository.findByEnrollmentIdAndLessonId(enrollmentId, lessonId))
                .willReturn(Optional.of(existing));
        given(progressRepository.save(any(LessonProgress.class)))
                .willAnswer(inv -> inv.getArgument(0));

        UpdateProgressRequest request = new UpdateProgressRequest();
        request.setWatchDurationSec(200);   // student rewinds back to the start of the video
        request.setLastPositionSec(200);
        request.setVideoDurationSec(800);

        var response = progressService.updateProgress(userId, courseId, lessonId, request);

        // watchDurationSec stays at 700 (the highest value ever reached), but lastPositionSec changes to 200
        assertThat(response.getWatchDurationSec()).isEqualTo(700);
        assertThat(response.getLastPositionSec()).isEqualTo(200);
    }
}