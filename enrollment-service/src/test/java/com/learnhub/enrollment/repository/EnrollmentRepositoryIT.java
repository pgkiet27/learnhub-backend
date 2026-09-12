package com.learnhub.enrollment.repository;

import com.learnhub.enrollment.AbstractIntegrationTest;
import com.learnhub.enrollment.entity.Enrollment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EnrollmentRepository Integration Tests")
@Transactional
class EnrollmentRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Test
    @DisplayName("Save and findByUserIdAndCourseId — succeeds")
    void saveAndFind_ShouldWork() {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        Enrollment enrollment = Enrollment.builder()
                .userId(userId).courseId(courseId)
                .courseTitle("Test Course").totalLessons(5)
                .build();
        enrollmentRepository.save(enrollment);

        var found = enrollmentRepository.findByUserIdAndCourseId(userId, courseId);

        assertThat(found).isPresent();
        assertThat(found.get().getCourseTitle()).isEqualTo("Test Course");
        assertThat(found.get().getProgressPercent()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("UNIQUE(user_id, course_id) — duplicate enroll is blocked by the DB")
    void save_DuplicateUserCourse_ShouldViolateUniqueConstraint() {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        enrollmentRepository.saveAndFlush(Enrollment.builder()
                .userId(userId).courseId(courseId).courseTitle("Course A").build());

        Enrollment duplicate = Enrollment.builder()
                .userId(userId).courseId(courseId).courseTitle("Course A (duplicate)").build();

        assertThatThrownBy(() -> enrollmentRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}