package com.learnhub.course.service;

import com.learnhub.common.dto.PageResponse;
import com.learnhub.common.exception.ConflictException;
import com.learnhub.common.exception.ForbiddenException;
import com.learnhub.common.exception.ResourceNotFoundException;
import com.learnhub.course.dto.request.CreateReviewRequest;
import com.learnhub.course.dto.response.ReviewResponse;
import com.learnhub.course.entity.Course;
import com.learnhub.course.entity.Review;
import com.learnhub.course.repository.CourseRepository;
import com.learnhub.course.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final CourseRepository courseRepository;

    /**
     * A student reviews a course.
     * Requires: already enrolled and completed at least 50% of the course
     * (checked against the Enrollment Service — implemented later in B4).
     */
    @Transactional
    public ReviewResponse createReview(UUID courseId, CreateReviewRequest request,
                                       UUID userId) {
        // Check whether the user has already reviewed
        if (reviewRepository.existsByCourseIdAndUserId(courseId, userId)) {
            throw new ConflictException("ALREADY_REVIEWED",
                    "Bạn đã đánh giá khóa học này rồi");
        }

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "COURSE_NOT_FOUND", "Khóa học không tồn tại"));

        if (course.getStatus() != Course.Status.published) {
            throw new ResourceNotFoundException("COURSE_NOT_FOUND",
                    "Khóa học không tồn tại");
        }

        Review review = Review.builder()
                .course(course)
                .userId(userId)
                .rating(request.getRating().shortValue())
                .comment(request.getComment())
                .build();

        Review saved = reviewRepository.save(review);

        // Update the course's avg_rating and total_reviews
        courseRepository.updateRatingStats(courseId);

        log.info("Review created for course: {} by user: {}", courseId, userId);

        return toReviewResponse(saved);
    }

    /**
     * Gets the list of reviews for a course.
     */
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getReviewsByCourse(UUID courseId, Pageable pageable) {
        return PageResponse.of(
                reviewRepository.findByCourseIdAndIsVisibleTrue(courseId, pageable)
                        .map(this::toReviewResponse));
    }

    /**
     * A student edits their own review.
     */
    @Transactional
    public ReviewResponse updateReview(UUID courseId, UUID reviewId,
                                       CreateReviewRequest request, UUID userId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "REVIEW_NOT_FOUND", "Review không tồn tại"));

        if (!review.getUserId().equals(userId)) {
            throw new ForbiddenException("NOT_REVIEW_OWNER",
                    "Bạn không có quyền sửa review này");
        }

        review.setRating(request.getRating().shortValue());
        review.setComment(request.getComment());

        Review saved = reviewRepository.save(review);
        courseRepository.updateRatingStats(courseId);

        return toReviewResponse(saved);
    }

    /**
     * A student deletes their own review.
     */
    @Transactional
    public void deleteReview(UUID courseId, UUID reviewId, UUID userId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "REVIEW_NOT_FOUND", "Review không tồn tại"));

        if (!review.getUserId().equals(userId)) {
            throw new ForbiddenException("NOT_REVIEW_OWNER",
                    "Bạn không có quyền xóa review này");
        }

        reviewRepository.delete(review);
        courseRepository.updateRatingStats(courseId);
    }

    /**
     * Admin hides a review that violates rules.
     */
    @Transactional
    public void hideReview(UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "REVIEW_NOT_FOUND", "Review không tồn tại"));

        review.setVisible(false);
        reviewRepository.save(review);
        courseRepository.updateRatingStats(review.getCourse().getId());
    }

    public ReviewResponse toReviewResponse(Review review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .courseId(review.getCourse().getId())
                .userId(review.getUserId())
                .rating(review.getRating())
                .comment(review.getComment())
                .isVisible(review.isVisible())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}