package com.learnhub.course.repository;

import com.learnhub.course.AbstractIntegrationTest;
import com.learnhub.course.entity.Course;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CourseRepository Integration Tests")
@Transactional
class CourseRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private UUID instructorId;

    @BeforeEach
    void setUp() {
        instructorId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Save và findBySlug — thành công")
    void saveAndFindBySlug_ShouldWork() {
        // Given
        Course course = Course.builder()
                .instructorId(instructorId)
                .title("Test Course")
                .slug("test-course-" + System.currentTimeMillis())
                .price(BigDecimal.valueOf(299000))
                .status(Course.Status.draft)
                .level(Course.Level.beginner)
                .language("vi")
                .tags(new String[]{"test"})
                .requirements(new String[]{})
                .objectives(new String[]{})
                .build();

        // When
        Course saved = courseRepository.save(course);
        Optional<Course> found = courseRepository.findBySlug(saved.getSlug());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Test Course");
        assertThat(found.get().getInstructorId()).isEqualTo(instructorId);
        assertThat(found.get().getStatus()).isEqualTo(Course.Status.draft);
    }

    @Test
    @DisplayName("findByStatus — chỉ trả về published courses")
    void findByStatus_Published_ShouldReturnOnlyPublished() {
        // Given
        String suffix = String.valueOf(System.currentTimeMillis());

        Course publishedCourse = Course.builder()
                .instructorId(instructorId)
                .title("Published Course")
                .slug("published-" + suffix)
                .price(BigDecimal.valueOf(499000))
                .status(Course.Status.published)
                .level(Course.Level.all)
                .language("vi")
                .tags(new String[]{})
                .requirements(new String[]{})
                .objectives(new String[]{})
                .build();

        Course draftCourse = Course.builder()
                .instructorId(instructorId)
                .title("Draft Course")
                .slug("draft-" + suffix)
                .price(BigDecimal.ZERO)
                .status(Course.Status.draft)
                .level(Course.Level.all)
                .language("vi")
                .tags(new String[]{})
                .requirements(new String[]{})
                .objectives(new String[]{})
                .build();

        courseRepository.save(publishedCourse);
        courseRepository.save(draftCourse);

        // When
        Page<Course> page = courseRepository.findByStatus(
                Course.Status.published, PageRequest.of(0, 10));

        // Then
        assertThat(page.getContent())
                .allMatch(c -> c.getStatus() == Course.Status.published);
    }

    @Test
    @DisplayName("existsBySlug — trả về true khi slug tồn tại")
    void existsBySlug_ExistingSlug_ShouldReturnTrue() {
        // Given
        String slug = "unique-slug-" + System.currentTimeMillis();
        Course course = Course.builder()
                .instructorId(instructorId)
                .title("Test")
                .slug(slug)
                .price(BigDecimal.ZERO)
                .status(Course.Status.draft)
                .level(Course.Level.all)
                .language("vi")
                .tags(new String[]{})
                .requirements(new String[]{})
                .objectives(new String[]{})
                .build();
        courseRepository.save(course);

        // When & Then
        assertThat(courseRepository.existsBySlug(slug)).isTrue();
        assertThat(courseRepository.existsBySlug("non-existent-slug")).isFalse();
    }

    @Test
    @DisplayName("Full-text search — tìm được khóa học theo keyword")
    void searchByKeyword_ShouldReturnMatchingCourses() {
        // Given
        String suffix = String.valueOf(System.currentTimeMillis());

        Course javaClass = Course.builder()
                .instructorId(instructorId)
                .title("Lập trình Java nâng cao")
                .slug("java-nang-cao-" + suffix)
                .shortDescription("Học Java Spring Boot")
                .price(BigDecimal.valueOf(499000))
                .status(Course.Status.published)
                .level(Course.Level.intermediate)
                .language("vi")
                .tags(new String[]{"java"})
                .requirements(new String[]{})
                .objectives(new String[]{})
                .build();

        Course pythonClass = Course.builder()
                .instructorId(instructorId)
                .title("Python cho người mới")
                .slug("python-moi-" + suffix)
                .shortDescription("Học Python cơ bản")
                .price(BigDecimal.valueOf(299000))
                .status(Course.Status.published)
                .level(Course.Level.beginner)
                .language("vi")
                .tags(new String[]{"python"})
                .requirements(new String[]{})
                .objectives(new String[]{})
                .build();

        courseRepository.save(javaClass);
        courseRepository.save(pythonClass);

        // When
        Page<Course> results = courseRepository.searchByKeyword(
                "Java", PageRequest.of(0, 10));

        // Then
        assertThat(results.getContent())
                .anyMatch(c -> c.getTitle().contains("Java"));
        // The Python course does not appear in the "Java" search results
        assertThat(results.getContent())
                .noneMatch(c -> c.getTitle().contains("Python"));
    }
}