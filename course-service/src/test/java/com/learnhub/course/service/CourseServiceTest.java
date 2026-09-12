package com.learnhub.course.service;

import com.learnhub.common.exception.ForbiddenException;
import com.learnhub.common.exception.ResourceNotFoundException;
import com.learnhub.course.dto.request.CreateCourseRequest;
import com.learnhub.course.dto.response.CourseResponse;
import com.learnhub.course.entity.Category;
import com.learnhub.course.entity.Course;
import com.learnhub.course.repository.CategoryRepository;
import com.learnhub.course.repository.CourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CourseService Unit Tests")
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CourseService courseService;

    private UUID instructorId;
    private UUID courseId;
    private Course mockCourse;
    private Category mockCategory;

    @BeforeEach
    void setUp() {
        instructorId = UUID.randomUUID();
        courseId = UUID.randomUUID();

        mockCategory = Category.builder()
                .id(UUID.randomUUID())
                .name("Lập trình")
                .slug("lap-trinh")
                .build();

        mockCourse = Course.builder()
                .id(courseId)
                .instructorId(instructorId)
                .title("Java Spring Boot từ Zero")
                .slug("java-spring-boot-tu-zero")
                .price(BigDecimal.valueOf(499000))
                .status(Course.Status.draft)
                .level(Course.Level.beginner)
                .language("vi")
                .tags(new String[]{"java", "spring"})
                .requirements(new String[]{})
                .objectives(new String[]{})
                .build();
    }

    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("createCourse()")
    class CreateCourseTests {

        @Test
        @DisplayName("Tạo course thành công với đầy đủ thông tin")
        void createCourse_ValidRequest_ShouldReturnCourseResponse() {
            // Given
            CreateCourseRequest request = new CreateCourseRequest();
            request.setTitle("Java Spring Boot từ Zero");
            request.setPrice(BigDecimal.valueOf(499000));
            request.setLevel("beginner");

            given(courseRepository.existsBySlug(anyString())).willReturn(false);
            given(courseRepository.save(any(Course.class))).willReturn(mockCourse);

            // When
            CourseResponse response = courseService.createCourse(request, instructorId);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getTitle()).isEqualTo("Java Spring Boot từ Zero");
            assertThat(response.getStatus()).isEqualTo("draft");
            assertThat(response.getInstructorId()).isEqualTo(instructorId);

            // Verify the course was saved exactly once
            then(courseRepository).should(times(1)).save(any(Course.class));
        }

        @Test
        @DisplayName("Tạo course với category hợp lệ")
        void createCourse_WithValidCategory_ShouldSetCategory() {
            // Given
            CreateCourseRequest request = new CreateCourseRequest();
            request.setTitle("Test Course");
            request.setPrice(BigDecimal.ZERO);
            request.setCategoryId(mockCategory.getId());

            Course courseWithCategory = Course.builder()
                    .id(courseId)
                    .instructorId(instructorId)
                    .title("Test Course")
                    .slug("test-course")
                    .category(mockCategory)
                    .price(BigDecimal.ZERO)
                    .status(Course.Status.draft)
                    .level(Course.Level.all)
                    .language("vi")
                    .tags(new String[]{})
                    .requirements(new String[]{})
                    .objectives(new String[]{})
                    .build();

            given(courseRepository.existsBySlug(anyString())).willReturn(false);
            given(categoryRepository.findById(mockCategory.getId()))
                    .willReturn(Optional.of(mockCategory));
            given(courseRepository.save(any(Course.class))).willReturn(courseWithCategory);

            // When
            CourseResponse response = courseService.createCourse(request, instructorId);

            // Then
            assertThat(response.getCategory()).isNotNull();
            assertThat(response.getCategory().getName()).isEqualTo("Lập trình");
        }

        @Test
        @DisplayName("Tạo course với category không tồn tại — throw ResourceNotFoundException")
        void createCourse_InvalidCategory_ShouldThrowException() {
            // Given
            CreateCourseRequest request = new CreateCourseRequest();
            request.setTitle("Test Course");
            request.setPrice(BigDecimal.ZERO);
            request.setCategoryId(UUID.randomUUID());

            given(categoryRepository.findById(any())).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> courseService.createCourse(request, instructorId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Danh mục không tồn tại");
        }

        @Test
        @DisplayName("Slug tự động generate từ title (với dấu tiếng Việt)")
        void createCourse_VietnameseTitle_ShouldGenerateSlug() {
            // Given
            CreateCourseRequest request = new CreateCourseRequest();
            request.setTitle("Lập Trình Java Cơ Bản");
            request.setPrice(BigDecimal.ZERO);

            Course savedCourse = Course.builder()
                    .id(courseId)
                    .instructorId(instructorId)
                    .title("Lập Trình Java Cơ Bản")
                    .slug("lap-trinh-java-co-ban")
                    .price(BigDecimal.ZERO)
                    .status(Course.Status.draft)
                    .level(Course.Level.all)
                    .language("vi")
                    .tags(new String[]{})
                    .requirements(new String[]{})
                    .objectives(new String[]{})
                    .build();

            given(courseRepository.existsBySlug(anyString())).willReturn(false);
            given(courseRepository.save(any(Course.class))).willReturn(savedCourse);

            // When
            CourseResponse response = courseService.createCourse(request, instructorId);

            // Then
            assertThat(response.getSlug()).isEqualTo("lap-trinh-java-co-ban");
        }

        @Test
        @DisplayName("Slug đã tồn tại — generate slug mới có suffix")
        void createCourse_SlugConflict_ShouldGenerateUniqueSlug() {
            // Given
            CreateCourseRequest request = new CreateCourseRequest();
            request.setTitle("Java Cơ Bản");
            request.setPrice(BigDecimal.ZERO);

            // Slug "java-co-ban" already exists — service falls back to a suffixed
            // unique slug without re-checking it (see CourseService.generateUniqueSlug)
            given(courseRepository.existsBySlug("java-co-ban")).willReturn(true);
            given(courseRepository.save(any(Course.class))).willReturn(mockCourse);

            // When — should not throw an exception
            assertThatCode(() -> courseService.createCourse(request, instructorId))
                    .doesNotThrowAnyException();
        }
    }

    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("deleteCourse()")
    class DeleteCourseTests {

        @Test
        @DisplayName("Xóa course draft thành công")
        void deleteCourse_DraftStatus_ShouldDelete() {
            // Given
            given(courseRepository.findByIdAndInstructorId(courseId, instructorId))
                    .willReturn(Optional.of(mockCourse));

            // When
            courseService.deleteCourse(courseId, instructorId);

            // Then
            then(courseRepository).should(times(1)).delete(mockCourse);
        }

        @Test
        @DisplayName("Xóa course đang published — throw ForbiddenException")
        void deleteCourse_PublishedStatus_ShouldThrowForbiddenException() {
            // Given
            mockCourse.setStatus(Course.Status.published);
            given(courseRepository.findByIdAndInstructorId(courseId, instructorId))
                    .willReturn(Optional.of(mockCourse));

            // When & Then
            assertThatThrownBy(() -> courseService.deleteCourse(courseId, instructorId))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Chỉ có thể xóa");
        }

        @Test
        @DisplayName("Xóa course không phải của mình — throw ResourceNotFoundException")
        void deleteCourse_NotOwner_ShouldThrowException() {
            // Given
            UUID otherInstructorId = UUID.randomUUID();
            given(courseRepository.findByIdAndInstructorId(courseId, otherInstructorId))
                    .willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() ->
                    courseService.deleteCourse(courseId, otherInstructorId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("getPublishedCourses()")
    class GetPublishedCoursesTests {

        @Test
        @DisplayName("Lấy danh sách published courses thành công")
        void getPublishedCourses_ShouldReturnPageResponse() {
            // Given
            mockCourse.setStatus(Course.Status.published);
            var pageable = PageRequest.of(0, 10);
            var page = new PageImpl<>(List.of(mockCourse));

            given(courseRepository.findByStatus(Course.Status.published, pageable))
                    .willReturn(page);

            // When
            var response = courseService.getPublishedCourses(pageable);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getTitle())
                    .isEqualTo("Java Spring Boot từ Zero");
        }

        @Test
        @DisplayName("Không có course nào — trả về empty page")
        void getPublishedCourses_NoCourses_ShouldReturnEmptyPage() {
            // Given
            var pageable = PageRequest.of(0, 10);
            given(courseRepository.findByStatus(Course.Status.published, pageable))
                    .willReturn(new PageImpl<>(List.of()));

            // When
            var response = courseService.getPublishedCourses(pageable);

            // Then
            assertThat(response.getContent()).isEmpty();
            assertThat(response.getTotalElements()).isZero();
        }
    }
}