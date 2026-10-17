package com.learnhub.enrollment.repository;

import com.learnhub.enrollment.entity.Enrollment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    Optional<Enrollment> findByUserIdAndCourseId(UUID userId, UUID courseId);

    boolean existsByUserIdAndCourseId(UUID userId, UUID courseId);

    Page<Enrollment> findByUserIdOrderByLastAccessedAtDesc(UUID userId, Pageable pageable);

    Page<Enrollment> findByUserIdAndIsCompletedOrderByLastAccessedAtDesc(
            UUID userId, boolean isCompleted, Pageable pageable);

    // Churn prediction

    Page<Enrollment> findByIsCompletedFalse(Pageable pageable);

    List<Enrollment> findByCourseIdAndIsCompletedFalseAndChurnRiskLevel(UUID courseId, String churnRiskLevel);

    @Query("""
            SELECT e FROM Enrollment e WHERE e.courseId = :courseId
            ORDER BY e.isCompleted ASC, e.churnScore DESC NULLS LAST, e.enrolledAt DESC
            """)
    Page<Enrollment> findStudentsOfCourse(UUID courseId, Pageable pageable);

    @Query("""
            SELECT e FROM Enrollment e
            WHERE e.courseId = :courseId AND e.isCompleted = false AND e.churnRiskLevel = :riskLevel
            ORDER BY e.churnScore DESC
            """)
    Page<Enrollment> findStudentsOfCourseByRisk(UUID courseId, String riskLevel, Pageable pageable);

    @Query("""
            SELECT COUNT(e) AS total,
                   SUM(CASE WHEN e.isCompleted = true THEN 1 ELSE 0 END) AS completed,
                   SUM(CASE WHEN e.isCompleted = false AND e.lastAccessedAt IS NULL THEN 1 ELSE 0 END) AS notStarted,
                   SUM(CASE WHEN e.isCompleted = false AND e.churnRiskLevel = 'high' THEN 1 ELSE 0 END) AS highRisk,
                   SUM(CASE WHEN e.isCompleted = false AND e.churnRiskLevel = 'medium' THEN 1 ELSE 0 END) AS mediumRisk,
                   MAX(e.churnPredictedAt) AS lastPredictedAt
            FROM Enrollment e WHERE e.courseId = :courseId
            """)
    CourseStudentStats getCourseStudentStats(UUID courseId);

    interface CourseStudentStats {
        long getTotal();

        Long getCompleted();

        Long getNotStarted();

        Long getHighRisk();

        Long getMediumRisk();

        Instant getLastPredictedAt();
    }
}
