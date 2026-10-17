package com.learnhub.enrollment.service;

import com.learnhub.common.event.EnrollmentCreatedEvent;
import com.learnhub.common.exception.BadRequestException;
import com.learnhub.common.exception.ConflictException;
import com.learnhub.common.exception.ResourceNotFoundException;
import com.learnhub.enrollment.client.CourseServiceClient;
import com.learnhub.enrollment.config.RabbitMQConfig;
import com.learnhub.enrollment.dto.response.CourseInfoResponse;
import com.learnhub.enrollment.dto.response.EnrollmentResponse;
import com.learnhub.enrollment.dto.response.EnrollmentStatusResponse;
import com.learnhub.enrollment.entity.Enrollment;
import com.learnhub.enrollment.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseServiceClient courseServiceClient;
    private final RabbitTemplate rabbitTemplate;

    /**
     * A student self-enrolls in a FREE course.
     * Paid courses must go through Payment Service (B5) — direct self-enroll is not allowed here.
     */
    @Transactional
    public EnrollmentResponse enrollFreeCourse(UUID userId, UUID courseId) {
        CourseInfoResponse course = courseServiceClient.getCourse(courseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "COURSE_NOT_FOUND", "Course does not exist or is not published"));

        if (course.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            throw new BadRequestException("COURSE_NOT_FREE",
                    "This course is paid, please complete payment to enroll");
        }

        return toEnrollmentResponse(enrollCourse(userId, course));
    }

    /**
     * Shared enrollment-creation logic — called directly from enrollFreeCourse() (Step 4, free branch)
     * AND from PaymentSuccessEventListener (Step 5, paid branch). Price is not checked here —
     * verifying "whether enrollment is allowed" is the responsibility of the CALLER of this method.
     */
    @Transactional
    public Enrollment enrollCourse(UUID userId, CourseInfoResponse course) {
        if (enrollmentRepository.existsByUserIdAndCourseId(userId, course.getId())) {
            throw new ConflictException("ALREADY_ENROLLED", "You are already enrolled in this course");
        }

        Enrollment enrollment = Enrollment.builder()
                .userId(userId)
                .courseId(course.getId())
                .courseTitle(course.getTitle())
                .courseThumbnailUrl(course.getThumbnailUrl())
                .totalLessons(course.getTotalLessons())
                .build();

        Enrollment saved = enrollmentRepository.save(enrollment);
        publishEnrollmentCreatedEvent(saved);

        log.info("User {} enrolled course {}", userId, course.getId());
        return saved;
    }

    @Transactional
    public void unenrollCourse(UUID userId, UUID courseId) {
        Enrollment enrollment = enrollmentRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ENROLLMENT_NOT_FOUND", "You are not enrolled in this course"));

        enrollmentRepository.delete(enrollment);
        log.info("User {} unenrolled course {}", userId, courseId);
    }

    @Transactional(readOnly = true)
    public EnrollmentStatusResponse getEnrollmentStatus(UUID userId, UUID courseId) {
        return enrollmentRepository.findByUserIdAndCourseId(userId, courseId)
                .map(e -> EnrollmentStatusResponse.builder()
                        .enrolled(true)
                        .enrollment(toEnrollmentResponse(e))
                        .build())
                .orElseGet(() -> EnrollmentStatusResponse.builder()
                        .enrolled(false)
                        .build());
    }

    private void publishEnrollmentCreatedEvent(Enrollment enrollment) {
        EnrollmentCreatedEvent event = EnrollmentCreatedEvent.builder()
                .enrollmentId(enrollment.getId())
                .userId(enrollment.getUserId())
                .courseId(enrollment.getCourseId())
                .build();
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME, "enrollment.created", event);
        log.info("Published EnrollmentCreatedEvent for enrollment: {}", enrollment.getId());
    }

    public EnrollmentResponse toEnrollmentResponse(Enrollment e) {
        return EnrollmentResponse.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .courseId(e.getCourseId())
                .courseTitle(e.getCourseTitle())
                .courseThumbnailUrl(e.getCourseThumbnailUrl())
                .totalLessons(e.getTotalLessons())
                .completedLessons(e.getCompletedLessons())
                .progressPercent(e.getProgressPercent())
                .isCompleted(e.isCompleted())
                .enrolledAt(e.getEnrolledAt())
                .completedAt(e.getCompletedAt())
                .lastAccessedAt(e.getLastAccessedAt())
                .build();
    }
}