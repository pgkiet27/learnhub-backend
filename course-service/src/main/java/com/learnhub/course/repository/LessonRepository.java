package com.learnhub.course.repository;

import com.learnhub.course.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    List<Lesson> findBySectionIdOrderByDisplayOrderAsc(UUID sectionId);

    List<Lesson> findByCourseIdOrderByDisplayOrderAsc(UUID courseId);

    // Preview lessons (no purchase needed)
    List<Lesson> findByCourseIdAndIsPreviewTrueAndIsPublishedTrue(UUID courseId);

    @Query("SELECT COALESCE(MAX(l.displayOrder), 0) FROM Lesson l WHERE l.section.id = :sectionId")
    Integer findMaxDisplayOrderBySectionId(UUID sectionId);

    // Total stats for a course
    @Query("""
            SELECT COUNT(l), COALESCE(SUM(l.videoDuration), 0)
            FROM Lesson l WHERE l.courseId = :courseId AND l.isPublished = true
            """)
    Object[] findStatsForCourse(UUID courseId);
}