package com.learnhub.course.service;

import com.learnhub.common.dto.PageResponse;
import com.learnhub.common.exception.ForbiddenException;
import com.learnhub.common.exception.ResourceNotFoundException;
import com.learnhub.common.util.SlugUtils;
import com.learnhub.course.dto.request.CreateCourseRequest;
import com.learnhub.course.dto.request.UpdateCourseRequest;
import com.learnhub.course.dto.response.CourseResponse;
import com.learnhub.course.entity.Category;
import com.learnhub.course.entity.Course;
import com.learnhub.course.repository.CategoryRepository;
import com.learnhub.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;

    // Instructor: Create a new course

    @Transactional
    public CourseResponse createCourse(CreateCourseRequest request, UUID instructorId) {

        // Generate slug from title
        String slug = generateUniqueSlug(request.getTitle());

        // Find category if provided
        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "CATEGORY_NOT_FOUND", "Danh mục không tồn tại"));
        }

        Course course = Course.builder()
                .instructorId(instructorId)
                .category(category)
                .title(request.getTitle())
                .slug(slug)
                .shortDescription(request.getShortDescription())
                .description(request.getDescription())
                .price(request.getPrice())
                .discountPrice(request.getDiscountPrice())
                .level(request.getLevel() != null
                        ? Course.Level.valueOf(request.getLevel()) : Course.Level.all)
                .language(request.getLanguage() != null ? request.getLanguage() : "vi")
                .tags(request.getTags() != null
                        ? request.getTags().toArray(new String[0]) : new String[]{})
                .requirements(request.getRequirements() != null
                        ? request.getRequirements().toArray(new String[0]) : new String[]{})
                .objectives(request.getObjectives() != null
                        ? request.getObjectives().toArray(new String[0]) : new String[]{})
                .status(Course.Status.draft)
                .build();

        Course saved = courseRepository.save(course);
        log.info("Course created: {} by instructor: {}", saved.getId(), instructorId);

        return toCourseResponse(saved);
    }

    // Instructor: Update a course

    @Transactional
    @CacheEvict(value = "courses", key = "#courseId")
    public CourseResponse updateCourse(UUID courseId, UpdateCourseRequest request,
                                       UUID instructorId) {

        Course course = findCourseOwnedByInstructor(courseId, instructorId);

        // Only allow editing when in draft or rejected status
        if (course.getStatus() == Course.Status.pending ||
                course.getStatus() == Course.Status.published) {
            throw new ForbiddenException("COURSE_NOT_EDITABLE",
                    "Không thể chỉnh sửa khóa học đang chờ duyệt hoặc đã published");
        }

        if (request.getTitle() != null && !request.getTitle().equals(course.getTitle())) {
            course.setTitle(request.getTitle());
            // Update slug if title changed
            course.setSlug(generateUniqueSlug(request.getTitle()));
        }
        if (request.getShortDescription() != null)
            course.setShortDescription(request.getShortDescription());
        if (request.getDescription() != null)
            course.setDescription(request.getDescription());
        if (request.getPrice() != null)
            course.setPrice(request.getPrice());
        if (request.getDiscountPrice() != null)
            course.setDiscountPrice(request.getDiscountPrice());
        if (request.getThumbnailUrl() != null)
            course.setThumbnailUrl(request.getThumbnailUrl());
        if (request.getPreviewVideoUrl() != null)
            course.setPreviewVideoUrl(request.getPreviewVideoUrl());
        if (request.getTags() != null)
            course.setTags(request.getTags().toArray(new String[0]));
        if (request.getRequirements() != null)
            course.setRequirements(request.getRequirements().toArray(new String[0]));
        if (request.getObjectives() != null)
            course.setObjectives(request.getObjectives().toArray(new String[0]));
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "CATEGORY_NOT_FOUND", "Danh mục không tồn tại"));
            course.setCategory(category);
        }

        return toCourseResponse(courseRepository.save(course));
    }

    // Instructor: Update thumbnail/preview video

    @Transactional
    public CourseResponse updateCourseThumbnail(UUID courseId, String thumbnailUrl,
                                                UUID instructorId) {
        Course course = findCourseOwnedByInstructor(courseId, instructorId);
        course.setThumbnailUrl(thumbnailUrl);
        return toCourseResponse(courseRepository.save(course));
    }

    // Public: Get list of published courses

    @Transactional(readOnly = true)
    @Cacheable(value = "courses", key = "'list_' + #pageable.pageNumber")
    public PageResponse<CourseResponse> getPublishedCourses(Pageable pageable) {
        Page<Course> page = courseRepository.findByStatus(
                Course.Status.published, pageable);
        return PageResponse.of(page.map(this::toCourseResponse));
    }

    // Public: Get course details by slug

    @Transactional(readOnly = true)
    @Cacheable(value = "courses", key = "#slug")
    public CourseResponse getCourseBySlug(String slug) {
        Course course = courseRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "COURSE_NOT_FOUND", "Khóa học không tồn tại"));

        // Only visible publicly if published
        if (course.getStatus() != Course.Status.published) {
            throw new ResourceNotFoundException("COURSE_NOT_FOUND",
                    "Khóa học không tồn tại");
        }

        return toCourseResponse(course);
    }

    // Instructor: Get list of their own courses

    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> getInstructorCourses(
            UUID instructorId, Pageable pageable) {
        Page<Course> page = courseRepository.findByInstructorId(instructorId, pageable);
        return PageResponse.of(page.map(this::toCourseResponse));
    }

    // Instructor: Delete a course (draft only)

    @Transactional
    public void deleteCourse(UUID courseId, UUID instructorId) {
        Course course = findCourseOwnedByInstructor(courseId, instructorId);

        if (course.getStatus() != Course.Status.draft) {
            throw new ForbiddenException("CANNOT_DELETE_COURSE",
                    "Chỉ có thể xóa khóa học ở trạng thái draft");
        }

        courseRepository.delete(course);
        log.info("Course deleted: {} by instructor: {}", courseId, instructorId);
    }

    // Private helpers

    private Course findCourseOwnedByInstructor(UUID courseId, UUID instructorId) {
        return courseRepository.findByIdAndInstructorId(courseId, instructorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "COURSE_NOT_FOUND",
                        "Khóa học không tồn tại hoặc bạn không có quyền truy cập"));
    }

    private String generateUniqueSlug(String title) {
        String slug = SlugUtils.toSlug(title);

        // If slug already exists → add a suffix
        if (courseRepository.existsBySlug(slug)) {
            slug = SlugUtils.toUniqueSlug(title);
        }

        return slug;
    }

    public CourseResponse toCourseResponse(Course course) {
        CourseResponse.CategoryInfo categoryInfo = null;
        if (course.getCategory() != null) {
            categoryInfo = CourseResponse.CategoryInfo.builder()
                    .id(course.getCategory().getId())
                    .name(course.getCategory().getName())
                    .slug(course.getCategory().getSlug())
                    .build();
        }

        return CourseResponse.builder()
                .id(course.getId())
                .instructorId(course.getInstructorId())
                .title(course.getTitle())
                .slug(course.getSlug())
                .shortDescription(course.getShortDescription())
                .description(course.getDescription())
                .thumbnailUrl(course.getThumbnailUrl())
                .previewVideoUrl(course.getPreviewVideoUrl())
                .level(course.getLevel().name())
                .language(course.getLanguage())
                .price(course.getPrice())
                .discountPrice(course.getDiscountPrice())
                .status(course.getStatus().name())
                .tags(course.getTags())
                .requirements(course.getRequirements())
                .objectives(course.getObjectives())
                .totalLessons(course.getTotalLessons())
                .totalDuration(course.getTotalDuration())
                .totalStudents(course.getTotalStudents())
                .totalReviews(course.getTotalReviews())
                .avgRating(course.getAvgRating())
                .isBestseller(course.isBestseller())
                .isFeatured(course.isFeatured())
                .publishedAt(course.getPublishedAt())
                .createdAt(course.getCreatedAt())
                .category(categoryInfo)
                .build();
    }
}