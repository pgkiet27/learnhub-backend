package com.learnhub.course.service;

import com.learnhub.common.exception.ResourceNotFoundException;
import com.learnhub.course.dto.request.CreateLessonRequest;
import com.learnhub.course.dto.response.LessonResponse;
import com.learnhub.course.entity.Lesson;
import com.learnhub.course.entity.Section;
import com.learnhub.course.repository.CourseRepository;
import com.learnhub.course.repository.LessonRepository;
import com.learnhub.course.repository.SectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LessonService {

    private final LessonRepository lessonRepository;
    private final SectionRepository sectionRepository;
    private final CourseRepository courseRepository;

    @Transactional
    public LessonResponse createLesson(UUID courseId, UUID sectionId,
                                       CreateLessonRequest request, UUID instructorId) {

        // Verify instructor owns the course
        courseRepository.findByIdAndInstructorId(courseId, instructorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "COURSE_NOT_FOUND", "Khóa học không tồn tại"));

        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SECTION_NOT_FOUND", "Section không tồn tại"));

        Integer maxOrder = lessonRepository.findMaxDisplayOrderBySectionId(sectionId);

        Lesson lesson = Lesson.builder()
                .section(section)
                .courseId(courseId)
                .title(request.getTitle())
                .description(request.getDescription())
                .lessonType(Lesson.LessonType.valueOf(
                        request.getLessonType() != null ? request.getLessonType() : "video"))
                .isPreview(Boolean.TRUE.equals(request.getIsPreview()))
                .displayOrder(maxOrder + 1)
                .build();

        Lesson saved = lessonRepository.save(lesson);

        // Update course stats
        courseRepository.updateLessonStats(courseId);

        return toLessonResponse(saved);
    }

    @Transactional
    public LessonResponse updateLessonMediaUrl(UUID lessonId, String videoUrl,
                                               Integer videoDuration, UUID instructorId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "LESSON_NOT_FOUND", "Bài học không tồn tại"));

        // Verify ownership
        courseRepository.findByIdAndInstructorId(lesson.getCourseId(), instructorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "COURSE_NOT_FOUND", "Không có quyền truy cập"));

        lesson.setVideoUrl(videoUrl);
        if (videoDuration != null) lesson.setVideoDuration(videoDuration);

        return toLessonResponse(lessonRepository.save(lesson));
    }

    @Transactional
    public LessonResponse publishLesson(UUID lessonId, UUID instructorId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "LESSON_NOT_FOUND", "Bài học không tồn tại"));

        courseRepository.findByIdAndInstructorId(lesson.getCourseId(), instructorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "COURSE_NOT_FOUND", "Không có quyền"));

        lesson.setPublished(true);
        lesson.setPublishedAt(Instant.now());

        // Update stats
        courseRepository.updateLessonStats(lesson.getCourseId());

        return toLessonResponse(lessonRepository.save(lesson));
    }

    public LessonResponse toLessonResponse(Lesson lesson) {
        return LessonResponse.builder()
                .id(lesson.getId())
                .title(lesson.getTitle())
                .description(lesson.getDescription())
                .lessonType(lesson.getLessonType().name())
                .videoUrl(lesson.getVideoUrl())
                .videoDuration(lesson.getVideoDuration())
                .documentUrl(lesson.getDocumentUrl())
                .content(lesson.getContent())
                .displayOrder(lesson.getDisplayOrder())
                .isPreview(lesson.isPreview())
                .isPublished(lesson.isPublished())
                .publishedAt(lesson.getPublishedAt())
                .build();
    }
}