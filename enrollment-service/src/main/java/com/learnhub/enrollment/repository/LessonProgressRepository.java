package com.learnhub.enrollment.repository;

import com.learnhub.enrollment.entity.LessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LessonProgressRepository extends JpaRepository<LessonProgress, UUID> {

    Optional<LessonProgress> findByEnrollmentIdAndLessonId(UUID enrollmentId, UUID lessonId);

    long countByEnrollmentIdAndIsCompletedTrue(UUID enrollmentId);

    @Query("SELECT p.lessonId FROM LessonProgress p WHERE p.enrollment.id = :enrollmentId AND p.isCompleted = true")
    List<UUID> findCompletedLessonIds(UUID enrollmentId);

    List<LessonProgress> findByEnrollmentIdIn(Collection<UUID> enrollmentIds);
}