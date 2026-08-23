package com.learnhub.course.service;

import com.learnhub.common.exception.BadRequestException;
import com.learnhub.course.AbstractIntegrationTest;
import com.learnhub.course.entity.Course;
import com.learnhub.course.entity.Lesson;
import com.learnhub.course.entity.Section;
import com.learnhub.course.repository.CourseRepository;
import com.learnhub.course.repository.LessonRepository;
import com.learnhub.course.repository.SectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.times;

@DisplayName("Course Approval Integration Tests")
@Transactional
class CourseApprovalIT extends AbstractIntegrationTest {

    @Autowired
    private CourseApprovalService approvalService;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private SectionRepository sectionRepository;
    @Autowired
    private LessonRepository lessonRepository;

    // Mock RabbitTemplate so a real RabbitMQ isn't needed when testing approval
    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    private UUID instructorId;
    private Course course;

    @BeforeEach
    void setUp() {
        instructorId = UUID.randomUUID();

        // Create a complete course with a published lesson
        course = Course.builder()
                .instructorId(instructorId)
                .title("Complete Course")
                .slug("complete-course-" + System.currentTimeMillis())
                .shortDescription("Mô tả ngắn")
                .description("Mô tả đầy đủ về khóa học này")
                .price(BigDecimal.valueOf(299000))
                .status(Course.Status.draft)
                .level(Course.Level.beginner)
                .language("vi")
                .tags(new String[]{"test"})
                .requirements(new String[]{})
                .objectives(new String[]{"Học được A", "Học được B"})
                .thumbnailUrl("https://s3.example.com/thumbnail.jpg")
                .build();
        course = courseRepository.save(course);

        // Add a section
        Section section = Section.builder()
                .course(course)
                .title("Chương 1")
                .displayOrder(1)
                .build();
        section = sectionRepository.save(section);
        // Keep the in-memory bidirectional relationship in sync — setUp() and the
        // @Test method share the same persistence context (class-level @Transactional),
        // so a later findById() returns this same cached "course" instance rather than
        // re-querying the DB; without this, course.getSections() would stay stale/empty.
        course.getSections().add(section);

        // Add a published lesson
        Lesson lesson = Lesson.builder()
                .section(section)
                .courseId(course.getId())
                .title("Bài 1: Giới thiệu")
                .lessonType(Lesson.LessonType.video)
                .videoUrl("https://s3.example.com/video.mp4")
                .videoDuration(1800)
                .displayOrder(1)
                .isPublished(true)
                .publishedAt(Instant.now())
                .build();
        lessonRepository.save(lesson);
        section.getLessons().add(lesson);

        // Reload the course with its sections/lessons
        course = courseRepository.findById(course.getId()).orElseThrow();
    }

    @Test
    @DisplayName("Submit for review — draft → pending")
    void submitForReview_ValidCourse_ShouldChangeToPending() {
        // When
        var response = approvalService.submitForReview(course.getId(), instructorId);

        // Then
        assertThat(response.getStatus()).isEqualTo("pending");

        // Verify in the DB
        Course updated = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(Course.Status.pending);
    }

    @Test
    @DisplayName("Submit without thumbnail — throw BadRequestException")
    void submitForReview_NoThumbnail_ShouldThrowException() {
        // Given — remove the thumbnail
        course.setThumbnailUrl(null);
        courseRepository.save(course);

        // When & Then
        assertThatThrownBy(() ->
                approvalService.submitForReview(course.getId(), instructorId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("thumbnail");
    }

    @Test
    @DisplayName("Approve course — pending → published + event published")
    void approveCourse_PendingCourse_ShouldPublishAndFireEvent() {
        // Given — already submitted
        course.setStatus(Course.Status.pending);
        courseRepository.save(course);

        // When
        var response = approvalService.approveCourse(course.getId());

        // Then
        assertThat(response.getStatus()).isEqualTo("published");
        assertThat(response.getPublishedAt()).isNotNull();

        // Verify the RabbitMQ event was published
        then(rabbitTemplate).should(times(1))
                .convertAndSend(anyString(), eq("course.approved"), any(Object.class));
    }

    @Test
    @DisplayName("Reject course — pending → rejected với reason")
    void rejectCourse_PendingCourse_ShouldSetRejectedWithReason() {
        // Given
        course.setStatus(Course.Status.pending);
        courseRepository.save(course);

        String reason = "Nội dung chưa đủ chất lượng, cần bổ sung thêm bài tập";

        // When
        var response = approvalService.rejectCourse(course.getId(), reason);

        // Then
        assertThat(response.getStatus()).isEqualTo("rejected");

        Course updated = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(updated.getRejectionReason()).isEqualTo(reason);
    }

    @Test
    @DisplayName("Reject without reason — throw BadRequestException")
    void rejectCourse_NoReason_ShouldThrowException() {
        // Given
        course.setStatus(Course.Status.pending);
        courseRepository.save(course);

        // When & Then
        assertThatThrownBy(() ->
                approvalService.rejectCourse(course.getId(), ""))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("lý do từ chối");
    }

    @Test
    @DisplayName("Resubmit sau khi bị reject — rejected → pending")
    void resubmitAfterRejection_ShouldChangeToPending() {
        // Given — set to rejected
        course.setStatus(Course.Status.rejected);
        course.setRejectionReason("Cần thêm nội dung");
        courseRepository.save(course);

        // When
        var response = approvalService.submitForReview(course.getId(), instructorId);

        // Then
        assertThat(response.getStatus()).isEqualTo("pending");
        // The rejection reason is cleared
        Course updated = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(updated.getRejectionReason()).isNull();
    }
}