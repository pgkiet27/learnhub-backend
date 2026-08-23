package com.learnhub.course.service;

import com.learnhub.common.dto.PageResponse;
import com.learnhub.course.dto.response.CourseResponse;
import com.learnhub.course.entity.Course;
import com.learnhub.course.repository.CourseRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service that handles course search.
 * <p>
 * Search strategy:
 * 1. Full-text search (PostgreSQL GIN index) if a keyword is given
 * 2. Filter by category, level, price range, rating
 * 3. Sort by relevance, price, rating, newest, popular
 * <p>
 * PostgreSQL Full-text search:
 * to_tsvector('simple', title || ' ' || description)
 *
 * @@ plainto_tsquery(' simple ', ' lap trinh java ')
 * <p>
 * 'simple' config = no stemming, works well for Vietnamese
 * GIN index already created in the V9 migration → fast queries
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseSearchService {

    private final CourseRepository courseRepository;
    private final CourseService courseService;
    private final EntityManager entityManager;

    /**
     * Searches courses with multiple filters.
     *
     * @param keyword    Search keyword (optional)
     * @param categoryId Filter by category (optional)
     * @param level      Filter by level: beginner|intermediate|advanced|all
     * @param minPrice   Minimum price
     * @param maxPrice   Maximum price
     * @param minRating  Minimum rating (1-5)
     * @param pageable   Pagination + sorting
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "search",
            key = "#keyword + '_' + #categoryId + '_' + #level + '_' + #minPrice + '_' + #maxPrice + '_' + #minRating + '_' + #pageable.pageNumber",
            condition = "#keyword != null && !#keyword.isBlank()")
    public PageResponse<CourseResponse> searchCourses(
            String keyword,
            String categoryId,
            String level,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Double minRating,
            Pageable pageable) {

        // Build a dynamic WHERE clause shared by both the data query and the count query
        StringBuilder where = new StringBuilder(" WHERE c.status = 'published' ");
        List<Object> params = new ArrayList<>();
        int paramIdx = 1;
        int keywordParamIdx = -1;

        // Full-text search
        if (keyword != null && !keyword.isBlank()) {
            keywordParamIdx = paramIdx;
            where.append("""
                    AND to_tsvector('simple', c.title || ' ' || COALESCE(c.short_description, ''))
                        @@ plainto_tsquery('simple', ?%d)
                    """.formatted(paramIdx++));
            params.add(keyword);
        }

        // Filter category
        if (categoryId != null && !categoryId.isBlank()) {
            where.append("AND c.category_id = ?%d\n".formatted(paramIdx++));
            params.add(UUID.fromString(categoryId));
        }

        // Filter level
        if (level != null && !level.isBlank() && !level.equals("all")) {
            where.append("AND c.level = ?%d\n".formatted(paramIdx++));
            params.add(level);
        }

        // Filter price range
        if (minPrice != null) {
            where.append("AND c.price >= ?%d\n".formatted(paramIdx++));
            params.add(minPrice);
        }
        if (maxPrice != null) {
            where.append("AND c.price <= ?%d\n".formatted(paramIdx++));
            params.add(maxPrice);
        }

        // Filter rating
        if (minRating != null) {
            where.append("AND c.avg_rating >= ?%d\n".formatted(paramIdx++));
            params.add(BigDecimal.valueOf(minRating));
        }

        // Sorting (reuses the keyword parameter index for ts_rank when relevance-sorted)
        String orderBy = buildOrderByClause(pageable.getSort(), keywordParamIdx);

        String dataSql = "SELECT c.* FROM courses c" + where + orderBy;
        String countSql = "SELECT COUNT(*) FROM courses c" + where;

        Query dataQuery = entityManager.createNativeQuery(dataSql, Course.class);
        Query countQuery = entityManager.createNativeQuery(countSql);
        for (int i = 0; i < params.size(); i++) {
            dataQuery.setParameter(i + 1, params.get(i));
            countQuery.setParameter(i + 1, params.get(i));
        }
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Course> courses = dataQuery.getResultList();
        long total = ((Number) countQuery.getSingleResult()).longValue();

        Page<Course> page = new PageImpl<>(courses, pageable, total);
        return PageResponse.of(page.map(courseService::toCourseResponse));
    }

    /**
     * Gets featured courses (bestseller + featured).
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "featured-courses")
    public List<CourseResponse> getFeaturedCourses() {
        return courseRepository
                .findTop10ByStatusOrderByTotalStudentsDesc(Course.Status.published)
                .stream()
                .map(courseService::toCourseResponse)
                .toList();
    }

    /**
     * Gets courses by category.
     */
    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> getCoursesByCategory(
            String categoryId, Pageable pageable) {
        Page<Course> page = courseRepository.findByCategoryIdAndStatus(
                java.util.UUID.fromString(categoryId),
                Course.Status.published,
                pageable);
        return PageResponse.of(page.map(courseService::toCourseResponse));
    }

    private String buildOrderByClause(Sort sort, int keywordParamIdx) {

        // If a keyword is given → default sort by relevance
        if (keywordParamIdx > 0 && sort.isUnsorted()) {
            return """
                    ORDER BY ts_rank(
                        to_tsvector('simple', title || ' ' || COALESCE(short_description, '')),
                        plainto_tsquery('simple', ?%d)
                    ) DESC
                    """.formatted(keywordParamIdx);
        }

        if (sort.isUnsorted()) {
            return "ORDER BY created_at DESC\n";
        }

        return sort.stream()
                .map(order -> {
                    String col = switch (order.getProperty()) {
                        case "price" -> "c.price";
                        case "avgRating" -> "c.avg_rating";
                        case "totalStudents" -> "c.total_students";
                        default -> "c.created_at";
                    };
                    return col + " " + (order.isAscending() ? "ASC" : "DESC");
                })
                .reduce((a, b) -> a + ", " + b)
                .map(s -> "ORDER BY " + s + "\n")
                .orElse("ORDER BY c.created_at DESC\n");
    }
}