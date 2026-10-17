package com.learnhub.course.service;

import com.learnhub.common.exception.ForbiddenException;
import com.learnhub.common.exception.ResourceNotFoundException;
import com.learnhub.course.dto.request.CreateLessonNoteRequest;
import com.learnhub.course.dto.response.LessonNoteResponse;
import com.learnhub.course.entity.Lesson;
import com.learnhub.course.entity.LessonNote;
import com.learnhub.course.repository.LessonNoteRepository;
import com.learnhub.course.repository.LessonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LessonNoteService {

    private final LessonNoteRepository noteRepository;
    private final LessonRepository lessonRepository;

    @Transactional
    public LessonNoteResponse createNote(UUID lessonId,
                                         CreateLessonNoteRequest request,
                                         UUID userId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "LESSON_NOT_FOUND", "Bài học không tồn tại"));

        LessonNote note = LessonNote.builder()
                .lesson(lesson)
                .userId(userId)
                .content(request.getContent())
                .timestampSec(request.getTimestampSec() != null ? request.getTimestampSec() : 0)
                .build();

        return toNoteResponse(noteRepository.save(note));
    }

    @Transactional(readOnly = true)
    public List<LessonNoteResponse> getNotesByLesson(UUID lessonId, UUID userId) {
        return noteRepository.findByLessonIdAndUserIdOrderByTimestampSecAsc(lessonId, userId)
                .stream()
                .map(this::toNoteResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public LessonNoteResponse updateNote(UUID noteId,
                                         CreateLessonNoteRequest request,
                                         UUID userId) {
        LessonNote note = findNoteOwnedByUser(noteId, userId);
        note.setContent(request.getContent());
        if (request.getTimestampSec() != null)
            note.setTimestampSec(request.getTimestampSec());
        return toNoteResponse(noteRepository.save(note));
    }

    @Transactional
    public void deleteNote(UUID noteId, UUID userId) {
        LessonNote note = findNoteOwnedByUser(noteId, userId);
        noteRepository.delete(note);
    }

    private LessonNote findNoteOwnedByUser(UUID noteId, UUID userId) {
        LessonNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "NOTE_NOT_FOUND", "Ghi chú không tồn tại"));
        if (!note.getUserId().equals(userId)) {
            throw new ForbiddenException("NOT_NOTE_OWNER",
                    "Bạn không có quyền chỉnh sửa ghi chú này");
        }
        return note;
    }

    public LessonNoteResponse toNoteResponse(LessonNote note) {
        return LessonNoteResponse.builder()
                .id(note.getId())
                .lessonId(note.getLesson().getId())
                .userId(note.getUserId())
                .content(note.getContent())
                .timestampSec(note.getTimestampSec())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }
}