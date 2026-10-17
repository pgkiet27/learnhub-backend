package com.learnhub.course.service;

import com.learnhub.course.dto.response.CategoryResponse;
import com.learnhub.course.entity.Category;
import com.learnhub.course.entity.Course;
import com.learnhub.course.repository.CategoryRepository;
import com.learnhub.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CourseRepository courseRepository;

    /**
     * Root categories with their children, each carrying the number of published courses.
     * <p>
     * The counts come from one grouped query rather than a COUNT per category, and are looked
     * up from a map while mapping.
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        Map<UUID, Long> publishedPerCategory = courseRepository
                .countByCategoryGroupedByCategory(Course.Status.published)
                .stream()
                .collect(Collectors.toMap(
                        CourseRepository.CategoryCourseCount::getCategoryId,
                        CourseRepository.CategoryCourseCount::getTotal));

        return categoryRepository.findAllRootCategories()
                .stream()
                .map(category -> toCategoryResponse(category, publishedPerCategory))
                .collect(Collectors.toList());
    }

    private CategoryResponse toCategoryResponse(
            Category category, Map<UUID, Long> publishedPerCategory) {

        List<CategoryResponse> children = category.getChildren()
                .stream()
                .filter(Category::isActive)
                .sorted(Comparator.comparing(
                        Category::getDisplayOrder,
                        Comparator.nullsLast(Integer::compareTo)))
                .map(child -> baseResponse(child, publishedPerCategory).build())
                .collect(Collectors.toList());

        return baseResponse(category, publishedPerCategory)
                .children(children)
                .build();
    }

    private CategoryResponse.CategoryResponseBuilder baseResponse(
            Category category, Map<UUID, Long> publishedPerCategory) {

        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .icon(category.getIcon())
                .displayOrder(category.getDisplayOrder())
                // A category with no published course is absent from the grouped query
                .courseCount(publishedPerCategory.getOrDefault(category.getId(), 0L).intValue());
    }
}
