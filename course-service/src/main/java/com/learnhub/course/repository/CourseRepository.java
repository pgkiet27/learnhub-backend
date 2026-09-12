package com.learnhub.course.repository;

import com.learnhub.course.entity.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {

    Optional<Course> findBySlug(String slug);

    boolean existsBySlug(String slug);

    // Get all courses of an instructor
    Page<Course> findByInstructorId(UUID instructorId, Pageable pageable);

    // Get published courses by category
    Page<Course> findByCategoryIdAndStatus(
            UUID categoryId, Course.Status status, Pageable pageable);

    // Get all published courses (for homepage)
    Page<Course> findByStatus(Course.Status status, Pageable pageable);

    // Full-text search (will be used in Part 3)
    @Query(value = """
            SELECT c.* FROM courses c
            WHERE c.status = 'published'
            AND to_tsvector('simple', c.title || ' ' || COALESCE(c.short_description, ''))
                @@ plainto_tsquery('simple', :keyword)
            ORDER BY ts_rank(
                to_tsvector('simple', c.title || ' ' || COALESCE(c.short_description, '')),
                plainto_tsquery('simple', :keyword)
            ) DESC
            """, nativeQuery = true)
    Page<Course> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // Update rating stats after a new review
    @Modifying
    @Query("""
            UPDATE Course c SET
                c.avgRating = (
                    SELECT COALESCE(AVG(CAST(r.rating AS double)), 0)
                    FROM Review r WHERE r.course.id = :courseId AND r.isVisible = true
                ),
                c.totalReviews = (
                    SELECT COUNT(r) FROM Review r
                    WHERE r.course.id = :courseId AND r.isVisible = true
                )
            WHERE c.id = :courseId
            """)
    void updateRatingStats(@Param("courseId") UUID courseId);

    // Update total_lessons and total_duration
    @Modifying
    @Query("""
            UPDATE Course c SET
                c.totalLessons = (
                    SELECT COUNT(l) FROM Lesson l
                    WHERE l.courseId = :courseId AND l.isPublished = true
                ),
                c.totalDuration = (
                    SELECT COALESCE(SUM(l.videoDuration), 0) FROM Lesson l
                    WHERE l.courseId = :courseId AND l.isPublished = true
                )
            WHERE c.id = :courseId
            """)
    void updateLessonStats(@Param("courseId") UUID courseId);

    // Instructor can only view their own courses
    @Query("SELECT c FROM Course c WHERE c.id = :id AND c.instructorId = :instructorId")
    Optional<Course> findByIdAndInstructorId(
            @Param("id") UUID id,
            @Param("instructorId") UUID instructorId);

    List<Course> findTop10ByStatusOrderByTotalStudentsDesc(Course.Status status);
}