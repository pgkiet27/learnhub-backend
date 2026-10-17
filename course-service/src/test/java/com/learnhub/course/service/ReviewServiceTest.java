package com.learnhub.course.service;

import com.learnhub.common.exception.ConflictException;
import com.learnhub.common.exception.ForbiddenException;
import com.learnhub.course.dto.request.CreateReviewRequest;
import com.learnhub.course.dto.response.ReviewResponse;
import com.learnhub.course.entity.Course;
import com.learnhub.course.entity.Review;
import com.learnhub.course.repository.CourseRepository;
import com.learnhub.course.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewService Unit Tests")
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private ReviewService reviewService;

    private UUID courseId;
    private UUID userId;
    private Course mockCourse;
    private Review mockReview;

    @BeforeEach
    void setUp() {
        courseId = UUID.randomUUID();
        userId = UUID.randomUUID();

        mockCourse = Course.builder()
                .id(courseId)
                .title("Test Course")
                .slug("test-course")
                .status(Course.Status.published)
                .price(BigDecimal.valueOf(299000))
                .level(Course.Level.beginner)
                .language("vi")
                .tags(new String[]{})
                .requirements(new String[]{})
                .objectives(new String[]{})
                .build();

        mockReview = Review.builder()
                .id(UUID.randomUUID())
                .course(mockCourse)
                .userId(userId)
                .rating((short) 5)
                .comment("Khóa học rất tốt!")
                .isVisible(true)
                .build();
    }

    @Test
    @DisplayName("Tạo review thành công — lần đầu review")
    void createReview_FirstTime_ShouldCreateSuccessfully() {
        // Given
        CreateReviewRequest request = new CreateReviewRequest();
        request.setRating(5);
        request.setComment("Khóa học rất tốt!");

        given(reviewRepository.existsByCourseIdAndUserId(courseId, userId))
                .willReturn(false);
        given(courseRepository.findById(courseId)).willReturn(Optional.of(mockCourse));
        given(reviewRepository.save(any(Review.class))).willReturn(mockReview);

        // When
        ReviewResponse response = reviewService.createReview(courseId, request, userId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRating()).isEqualTo((short) 5);
        assertThat(response.getComment()).isEqualTo("Khóa học rất tốt!");

        // Verify rating stats were updated
        then(courseRepository).should(times(1)).updateRatingStats(courseId);
    }

    @Test
    @DisplayName("Review lần 2 — throw ConflictException")
    void createReview_AlreadyReviewed_ShouldThrowConflictException() {
        // Given
        CreateReviewRequest request = new CreateReviewRequest();
        request.setRating(4);

        given(reviewRepository.existsByCourseIdAndUserId(courseId, userId))
                .willReturn(true);  // Already reviewed

        // When & Then
        assertThatThrownBy(() -> reviewService.createReview(courseId, request, userId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("đã đánh giá");

        // Verify no new review was saved
        then(reviewRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("Review khóa học chưa published — throw ResourceNotFoundException")
    void createReview_UnpublishedCourse_ShouldThrowException() {
        // Given
        CreateReviewRequest request = new CreateReviewRequest();
        request.setRating(3);

        Course draftCourse = Course.builder()
                .id(courseId)
                .title("Draft Course")
                .slug("draft-course")
                .status(Course.Status.draft)   // Not published yet
                .price(BigDecimal.ZERO)
                .level(Course.Level.all)
                .language("vi")
                .tags(new String[]{})
                .requirements(new String[]{})
                .objectives(new String[]{})
                .build();

        given(reviewRepository.existsByCourseIdAndUserId(courseId, userId))
                .willReturn(false);
        given(courseRepository.findById(courseId)).willReturn(Optional.of(draftCourse));

        // When & Then
        assertThatThrownBy(() -> reviewService.createReview(courseId, request, userId))
                .isInstanceOf(com.learnhub.common.exception.ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Xóa review của người khác — throw ForbiddenException")
    void deleteReview_NotOwner_ShouldThrowForbiddenException() {
        // Given
        UUID otherUserId = UUID.randomUUID();
        given(reviewRepository.findById(mockReview.getId()))
                .willReturn(Optional.of(mockReview));

        // When & Then
        assertThatThrownBy(() ->
                reviewService.deleteReview(courseId, mockReview.getId(), otherUserId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("không có quyền");
    }

    @Test
    @DisplayName("Xóa review thành công — avg_rating được cập nhật")
    void deleteReview_OwnReview_ShouldDeleteAndUpdateRating() {
        // Given
        given(reviewRepository.findById(mockReview.getId()))
                .willReturn(Optional.of(mockReview));

        // When
        reviewService.deleteReview(courseId, mockReview.getId(), userId);

        // Then
        then(reviewRepository).should(times(1)).delete(mockReview);
        then(courseRepository).should(times(1)).updateRatingStats(courseId);
    }
}