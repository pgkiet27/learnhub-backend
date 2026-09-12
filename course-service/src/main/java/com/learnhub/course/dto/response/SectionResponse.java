package com.learnhub.course.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class SectionResponse {
    private UUID id;
    private String title;
    private String description;
    private Integer displayOrder;
    private List<LessonResponse> lessons;
}