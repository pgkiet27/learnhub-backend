package com.learnhub.enrollment.service;

import com.learnhub.common.dto.PageResponse;
import com.learnhub.common.exception.BadRequestException;
import com.learnhub.common.exception.ForbiddenException;
import com.learnhub.common.exception.ResourceNotFoundException;
import com.learnhub.enrollment.churn.ChurnPredictionService;
import com.learnhub.enrollment.client.CourseServiceClient;
import com.learnhub.enrollment.client.UserServiceClient;
import com.learnhub.enrollment.client.dto.UserSummary;
import com.learnhub.enrollment.dto.response.CourseInfoResponse;
import com.learnhub.enrollment.dto.response.CourseStudentsResponse;
import com.learnhub.enrollment.entity.Enrollment;
import com.learnhub.enrollment.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseStudentService {

    private static final Set<String> RISK_LEVELS = Set.of("low", "medium", "high");

    private final EnrollmentRepository enrollmentRepository;
    private final CourseServiceClient courseServiceClient;
    private final UserServiceClient userServiceClient;
    private final ChurnPredictionService churnPredictionService;

    @Transactional(readOnly = true)
    public CourseStudentsResponse getStudents(UUID courseId, UUID userId, String role,
                                              String riskLevel, int page, int size) {
        CourseInfoResponse course = requireCourseAccess(courseId, userId, role);
        if (riskLevel != null && !RISK_LEVELS.contains(riskLevel)) {
            throw new BadRequestException("INVALID_RISK_LEVEL", "riskLevel must be low, medium or high");
        }

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<Enrollment> enrollments = riskLevel == null
                ? enrollmentRepository.findStudentsOfCourse(courseId, pageable)
                : enrollmentRepository.findStudentsOfCourseByRisk(courseId, riskLevel, pageable);
        Map<UUID, UserSummary> users = fetchUsers(enrollments.map(Enrollment::getUserId).toSet());

        EnrollmentRepository.CourseStudentStats s = enrollmentRepository.getCourseStudentStats(courseId);
        CourseStudentsResponse.Stats stats = new CourseStudentsResponse.Stats(
                s.getTotal(), orZero(s.getCompleted()), orZero(s.getNotStarted()),
                orZero(s.getHighRisk()), orZero(s.getMediumRisk()), s.getLastPredictedAt());

        return new CourseStudentsResponse(courseId, course.getTitle(), stats,
                PageResponse.of(enrollments.map(e -> toStudent(e, users.get(e.getUserId())))));
    }

    public int sendChurnReminders(UUID courseId, UUID userId, String role) {
        requireCourseAccess(courseId, userId, role);
        return churnPredictionService.remindHighRiskStudents(courseId);
    }

    private CourseInfoResponse requireCourseAccess(UUID courseId, UUID userId, String role) {
        CourseInfoResponse course = courseServiceClient.getCourse(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("COURSE_NOT_FOUND", "Khóa học không tồn tại"));
        if (!"admin".equals(role) && !userId.equals(course.getInstructorId())) {
            throw new ForbiddenException("NOT_COURSE_OWNER", "Bạn không phải giảng viên của khóa học này");
        }
        return course;
    }

    private Map<UUID, UserSummary> fetchUsers(Set<UUID> userIds) {
        try {
            return userServiceClient.getSummaries(userIds);
        } catch (Exception ex) {
            // The list is still useful without display names
            log.warn("Could not load user summaries from user-service: {}", ex.getMessage());
            return Map.of();
        }
    }

    private static CourseStudentsResponse.Student toStudent(Enrollment e, UserSummary user) {
        return new CourseStudentsResponse.Student(
                e.getUserId(),
                user != null ? user.fullName() : "",
                user != null ? user.avatarUrl() : null,
                e.getProgressPercent(),
                e.getCompletedLessons(),
                e.getTotalLessons(),
                e.isCompleted(),
                e.getEnrolledAt(),
                e.getLastAccessedAt(),
                e.getChurnScore(),
                e.getChurnRiskLevel(),
                e.getChurnPredictedAt(),
                e.getChurnRemindedAt());
    }

    private static long orZero(Long value) {
        return value == null ? 0 : value;
    }
}
