package com.learnhub.course.repository;

import com.learnhub.course.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Page<Review> findByCourseIdAndIsVisibleTrue(UUID courseId, Pageable pageable);

    Optional<Review> findByCourseIdAndUserId(UUID courseId, UUID userId);

    boolean existsByCourseIdAndUserId(UUID courseId, UUID userId);

    @Query("SELECT AVG(CAST(r.rating AS double)) FROM Review r WHERE r.course.id = :courseId AND r.isVisible = true")
    Double findAvgRatingByCourseId(UUID courseId);
}