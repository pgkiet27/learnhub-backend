package com.learnhub.enrollment.repository;

import com.learnhub.enrollment.entity.Enrollment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    Optional<Enrollment> findByUserIdAndCourseId(UUID userId, UUID courseId);

    boolean existsByUserIdAndCourseId(UUID userId, UUID courseId);

    Page<Enrollment> findByUserIdOrderByLastAccessedAtDesc(UUID userId, Pageable pageable);

    Page<Enrollment> findByUserIdAndIsCompletedOrderByLastAccessedAtDesc(
            UUID userId, boolean isCompleted, Pageable pageable);
}