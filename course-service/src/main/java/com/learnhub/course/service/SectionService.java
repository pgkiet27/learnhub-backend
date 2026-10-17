package com.learnhub.course.service;

import com.learnhub.common.exception.ForbiddenException;
import com.learnhub.common.exception.ResourceNotFoundException;
import com.learnhub.course.dto.request.CreateSectionRequest;
import com.learnhub.course.dto.response.SectionResponse;
import com.learnhub.course.entity.Course;
import com.learnhub.course.entity.Section;
import com.learnhub.course.repository.CourseRepository;
import com.learnhub.course.repository.SectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SectionService {

    private final SectionRepository sectionRepository;
    private final CourseRepository courseRepository;
    private final LessonService lessonService;

    @Transactional
    public SectionResponse createSection(UUID courseId, CreateSectionRequest request,
                                         UUID instructorId) {
        Course course = findEditableCourseByInstructor(courseId, instructorId);

        Integer maxOrder = sectionRepository.findMaxDisplayOrderByCourseId(courseId);

        Section section = Section.builder()
                .course(course)
                .title(request.getTitle())
                .description(request.getDescription())
                .displayOrder(maxOrder + 1)
                .build();

        return toSectionResponse(sectionRepository.save(section));
    }

    @Transactional
    public SectionResponse updateSection(UUID courseId, UUID sectionId,
                                         CreateSectionRequest request, UUID instructorId) {
        findEditableCourseByInstructor(courseId, instructorId);
        Section section = findSectionInCourse(sectionId, courseId);

        if (request.getTitle() != null) section.setTitle(request.getTitle());
        if (request.getDescription() != null) section.setDescription(request.getDescription());

        return toSectionResponse(sectionRepository.save(section));
    }

    @Transactional
    public void deleteSection(UUID courseId, UUID sectionId, UUID instructorId) {
        findEditableCourseByInstructor(courseId, instructorId);
        Section section = findSectionInCourse(sectionId, courseId);
        sectionRepository.delete(section);
    }

    @Transactional(readOnly = true)
    public List<SectionResponse> getSectionsByCourse(UUID courseId) {
        return sectionRepository.findByCourseIdOrderByDisplayOrderAsc(courseId)
                .stream()
                .map(this::toSectionResponse)
                .collect(Collectors.toList());
    }

    private Course findEditableCourseByInstructor(UUID courseId, UUID instructorId) {
        Course course = courseRepository.findByIdAndInstructorId(courseId, instructorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "COURSE_NOT_FOUND", "Khóa học không tồn tại"));

        if (course.getStatus() == Course.Status.pending) {
            throw new ForbiddenException("COURSE_PENDING",
                    "Không thể chỉnh sửa khóa học đang chờ duyệt");
        }
        return course;
    }

    private Section findSectionInCourse(UUID sectionId, UUID courseId) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SECTION_NOT_FOUND", "Section không tồn tại"));

        if (!section.getCourse().getId().equals(courseId)) {
            throw new ForbiddenException("SECTION_NOT_IN_COURSE",
                    "Section không thuộc khóa học này");
        }
        return section;
    }

    public SectionResponse toSectionResponse(Section section) {
        return SectionResponse.builder()
                .id(section.getId())
                .title(section.getTitle())
                .description(section.getDescription())
                .displayOrder(section.getDisplayOrder())
                .lessons(section.getLessons().stream()
                        .map(lessonService::toLessonResponse)
                        .collect(Collectors.toList()))
                .build();
    }
}