package com.learnhub.common.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
@Builder
public class PageResponse<T> {
    private List<T> content; // items list in current page
    private int page; // current page (start from 0)
    private int size; // maximum number of items of each page
    private long totalElements; // total of items all datasets
    private int totalPages; // total of pages
    private boolean first; // is this page is first page
    private boolean last; // is this page is last page

    public static <T> PageResponse<T> of(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

}
