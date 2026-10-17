package com.learnhub.course.service;

import com.learnhub.common.event.CourseApprovedEvent;
import com.learnhub.common.exception.BadRequestException;
import com.learnhub.common.exception.ResourceNotFoundException;
import com.learnhub.course.config.RabbitMQConfig;
import com.learnhub.course.dto.response.CourseResponse;
import com.learnhub.course.entity.Course;
import com.learnhub.course.repository.CourseRepository;
import com.learnhub.course.repository.LessonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Handles the course approval workflow.
 * <p>
 * State transitions:
 * draft/rejected → pending    : Instructor submit for review
 * pending → published         : Admin approve
 * pending → rejected          : Admin reject (with reason)
 * published → hidden          : Admin hide
 * hidden → published          : Admin unhide
 * published → draft           : Instructor unpublish
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseApprovalService {

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final RabbitTemplate rabbitTemplate;
    private final CourseService courseService;

    // ─── Instructor Actions ───────────────────────────────────

    /**
     * Instructor submits a course for admin review.
     * Validate: must have at least 1 published lesson.
     */
    @Transactional
    public CourseResponse submitForReview(UUID courseId, UUID instructorId) {
        Course course = findCourseByInstructor(courseId, instructorId);

        // Only allow submit from draft or rejected
        if (course.getStatus() != Course.Status.draft &&
                course.getStatus() != Course.Status.rejected) {
            throw new BadRequestException("INVALID_STATUS",
                    "Chỉ có thể submit khóa học ở trạng thái draft hoặc rejected. " +
                            "Trạng thái hiện tại: " + course.getStatus());
        }

        // Validate: must have at least 1 section and 1 lesson
        validateCourseReadyForReview(course);

        course.setStatus(Course.Status.pending);
        course.setRejectionReason(null); // Clear the old rejection reason

        log.info("Course {} submitted for review by instructor {}",
                courseId, instructorId);

        return courseService.toCourseResponse(courseRepository.save(course));
    }

    /**
     * Instructor pulls a course back to draft after it was published.
     */
    @Transactional
    public CourseResponse unpublish(UUID courseId, UUID instructorId) {
        Course course = findCourseByInstructor(courseId, instructorId);

        if (course.getStatus() != Course.Status.published) {
            throw new BadRequestException("INVALID_STATUS",
                    "Chỉ có thể unpublish khóa học đang published");
        }

        course.setStatus(Course.Status.draft);

        log.info("Course {} unpublished by instructor {}", courseId, instructorId);

        return courseService.toCourseResponse(courseRepository.save(course));
    }

    // ─── Admin Actions ────────────────────────────────────────

    /**
     * Admin approves a course → published.
     * Publishes CourseApprovedEvent so the Notification Service can email the instructor.
     */
    @Transactional
    public CourseResponse approveCourse(UUID courseId) {
        Course course = findPendingCourse(courseId);

        course.setStatus(Course.Status.published);
        course.setPublishedAt(Instant.now());
        course.setRejectionReason(null);

        Course saved = courseRepository.save(course);

        // Publish event — Notification Service will send the notification email
        publishCourseApprovedEvent(saved);

        log.info("Course {} approved and published", courseId);

        return courseService.toCourseResponse(saved);
    }

    /**
     * Admin rejects a course → rejected.
     * A rejection reason is mandatory so the instructor knows what to fix.
     */
    @Transactional
    public CourseResponse rejectCourse(UUID courseId, String rejectionReason) {
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new BadRequestException("REJECTION_REASON_REQUIRED",
                    "Vui lòng cung cấp lý do từ chối");
        }

        Course course = findPendingCourse(courseId);

        course.setStatus(Course.Status.rejected);
        course.setRejectionReason(rejectionReason);

        log.info("Course {} rejected: {}", courseId, rejectionReason);

        return courseService.toCourseResponse(courseRepository.save(course));
    }

    /**
     * Admin hides a course that violates rules.
     */
    @Transactional
    public CourseResponse hideCourse(UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "COURSE_NOT_FOUND", "Khóa học không tồn tại"));

        if (course.getStatus() != Course.Status.published) {
            throw new BadRequestException("INVALID_STATUS",
                    "Chỉ có thể ẩn khóa học đang published");
        }

        course.setStatus(Course.Status.hidden);
        return courseService.toCourseResponse(courseRepository.save(course));
    }

    /**
     * Admin unhides a course.
     */
    @Transactional
    public CourseResponse unhideCourse(UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "COURSE_NOT_FOUND", "Khóa học không tồn tại"));

        if (course.getStatus() != Course.Status.hidden) {
            throw new BadRequestException("INVALID_STATUS",
                    "Khóa học không đang ở trạng thái hidden");
        }

        course.setStatus(Course.Status.published);
        return courseService.toCourseResponse(courseRepository.save(course));
    }

    /**
     * Admin gets the list of pending courses.
     */
    @Transactional(readOnly = true)
    public com.learnhub.common.dto.PageResponse<CourseResponse> getPendingCourses(
            org.springframework.data.domain.Pageable pageable) {
        return com.learnhub.common.dto.PageResponse.of(
                courseRepository.findByStatus(Course.Status.pending, pageable)
                        .map(courseService::toCourseResponse));
    }

    // ─── Private helpers ──────────────────────────────────────

    private Course findCourseByInstructor(UUID courseId, UUID instructorId) {
        return courseRepository.findByIdAndInstructorId(courseId, instructorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "COURSE_NOT_FOUND",
                        "Khóa học không tồn tại hoặc bạn không có quyền"));
    }

    private Course findPendingCourse(UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "COURSE_NOT_FOUND", "Khóa học không tồn tại"));

        if (course.getStatus() != Course.Status.pending) {
            throw new BadRequestException("INVALID_STATUS",
                    "Chỉ có thể approve/reject khóa học đang pending. " +
                            "Trạng thái hiện tại: " + course.getStatus());
        }

        return course;
    }

    private void validateCourseReadyForReview(Course course) {
        // Must have title, description, thumbnail, price
        if (course.getThumbnailUrl() == null || course.getThumbnailUrl().isBlank()) {
            throw new BadRequestException("MISSING_THUMBNAIL",
                    "Vui lòng upload ảnh thumbnail trước khi submit");
        }

        if (course.getDescription() == null || course.getDescription().isBlank()) {
            throw new BadRequestException("MISSING_DESCRIPTION",
                    "Vui lòng nhập mô tả đầy đủ trước khi submit");
        }

        // Must have at least 1 published lesson
        long publishedLessons = course.getSections().stream()
                .flatMap(s -> s.getLessons().stream())
                .filter(l -> l.isPublished())
                .count();

        if (publishedLessons == 0) {
            throw new BadRequestException("NO_PUBLISHED_LESSONS",
                    "Khóa học phải có ít nhất 1 bài học đã published trước khi submit");
        }

        // Must have objectives
        if (course.getObjectives() == null || course.getObjectives().length == 0) {
            throw new BadRequestException("MISSING_OBJECTIVES",
                    "Vui lòng nhập mục tiêu khóa học trước khi submit");
        }
    }

    private void publishCourseApprovedEvent(Course course) {
        CourseApprovedEvent event = CourseApprovedEvent.builder()
                .courseId(course.getId())
                .instructorId(course.getInstructorId())
                .courseTitle(course.getTitle())
                .build();
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                "course.approved",
                event
        );
        log.info("Published CourseApprovedEvent for course: {}", course.getId());
    }
}