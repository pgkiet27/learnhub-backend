package com.learnhub.common.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

// helper create Pageable object from URL params
public final class PageUtils {
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 100;

    private PageUtils() {
    }

    public static Pageable of(int page, int size, String sortBy, String direction) {
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        int safePage = Math.max(page, 0);

        Sort sort = "desc".equalsIgnoreCase(direction)
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        return PageRequest.of(safePage, safeSize, sort);
    }

    public static Pageable of(int page, int size) {
        return of(page, size, "createdAt", "desc");
    }

    public static Pageable ofDefault() {
        return of(DEFAULT_PAGE, DEFAULT_SIZE);
    }
}
