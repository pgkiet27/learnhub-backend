package com.learnhub.enrollment.service;

import com.learnhub.common.exception.BadRequestException;
import com.learnhub.common.exception.ConflictException;
import com.learnhub.common.exception.ResourceNotFoundException;
import com.learnhub.enrollment.client.CourseServiceClient;
import com.learnhub.enrollment.dto.response.CourseInfoResponse;
import com.learnhub.enrollment.dto.response.EnrollmentResponse;
import com.learnhub.enrollment.entity.Enrollment;
import com.learnhub.enrollment.repository.EnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EnrollmentService Unit Tests")
class EnrollmentServiceTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private CourseServiceClient courseServiceClient;
    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private EnrollmentService enrollmentService;

    private UUID userId;
    private UUID courseId;
    private CourseInfoResponse freeCourse;
    private CourseInfoResponse paidCourse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        courseId = UUID.randomUUID();

        freeCourse = CourseInfoResponse.builder()
                .id(courseId).title("Java Basics").thumbnailUrl("thumb.jpg")
                .price(BigDecimal.ZERO).status("published")
                .instructorId(UUID.randomUUID()).totalLessons(10)
                .build();

        paidCourse = CourseInfoResponse.builder()
                .id(courseId).title("Java Advanced").thumbnailUrl("thumb2.jpg")
                .price(BigDecimal.valueOf(499000)).status("published")
                .instructorId(UUID.randomUUID()).totalLessons(20)
                .build();
    }

    @Nested
    @DisplayName("enrollFreeCourse()")
    class EnrollFreeCourseTests {

        @Test
        @DisplayName("Enroll in a free course successfully")
        void enrollFreeCourse_FreeCourse_ShouldSucceed() {
            given(courseServiceClient.getCourse(courseId)).willReturn(Optional.of(freeCourse));
            given(enrollmentRepository.existsByUserIdAndCourseId(userId, courseId)).willReturn(false);
            given(enrollmentRepository.save(any(Enrollment.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            EnrollmentResponse response = enrollmentService.enrollFreeCourse(userId, courseId);

            assertThat(response.getCourseTitle()).isEqualTo("Java Basics");
            assertThat(response.getTotalLessons()).isEqualTo(10);
            then(rabbitTemplate).should(times(1))
                    .convertAndSend(anyString(), eq("enrollment.created"), any(Object.class));
        }

        @Test
        @DisplayName("Enroll in a paid course — throws BadRequestException")
        void enrollFreeCourse_PaidCourse_ShouldThrowBadRequest() {
            given(courseServiceClient.getCourse(courseId)).willReturn(Optional.of(paidCourse));

            assertThatThrownBy(() -> enrollmentService.enrollFreeCourse(userId, courseId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("paid");

            then(enrollmentRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("Enroll in a course that doesn't exist / isn't published — throws ResourceNotFoundException")
        void enrollFreeCourse_CourseNotFound_ShouldThrowException() {
            given(courseServiceClient.getCourse(courseId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.enrollFreeCourse(userId, courseId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Enroll a 2nd time in the same course — throws ConflictException")
        void enrollFreeCourse_AlreadyEnrolled_ShouldThrowConflictException() {
            given(courseServiceClient.getCourse(courseId)).willReturn(Optional.of(freeCourse));
            given(enrollmentRepository.existsByUserIdAndCourseId(userId, courseId)).willReturn(true);

            assertThatThrownBy(() -> enrollmentService.enrollFreeCourse(userId, courseId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("already enrolled");

            then(rabbitTemplate).should(never()).convertAndSend(anyString(), anyString(), any(Object.class));
        }
    }

    @Nested
    @DisplayName("unenrollCourse()")
    class UnenrollCourseTests {

        @Test
        @DisplayName("Unenroll succeeds")
        void unenrollCourse_Enrolled_ShouldDelete() {
            Enrollment enrollment = Enrollment.builder().id(UUID.randomUUID())
                    .userId(userId).courseId(courseId).courseTitle("Java Basics").build();
            given(enrollmentRepository.findByUserIdAndCourseId(userId, courseId))
                    .willReturn(Optional.of(enrollment));

            enrollmentService.unenrollCourse(userId, courseId);

            then(enrollmentRepository).should(times(1)).delete(enrollment);
        }

        @Test
        @DisplayName("Unenroll when never enrolled — throws ResourceNotFoundException")
        void unenrollCourse_NotEnrolled_ShouldThrowException() {
            given(enrollmentRepository.findByUserIdAndCourseId(userId, courseId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.unenrollCourse(userId, courseId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getEnrollmentStatus()")
    class GetEnrollmentStatusTests {

        @Test
        @DisplayName("Not enrolled — enrolled = false, does not throw")
        void getEnrollmentStatus_NotEnrolled_ShouldReturnFalse() {
            given(enrollmentRepository.findByUserIdAndCourseId(userId, courseId))
                    .willReturn(Optional.empty());

            var status = enrollmentService.getEnrollmentStatus(userId, courseId);

            assertThat(status.isEnrolled()).isFalse();
            assertThat(status.getEnrollment()).isNull();
        }
    }
}