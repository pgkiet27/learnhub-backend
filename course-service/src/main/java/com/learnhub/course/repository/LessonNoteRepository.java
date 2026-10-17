package com.learnhub.course.repository;

import com.learnhub.course.entity.LessonNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LessonNoteRepository extends JpaRepository<LessonNote, UUID> {
    List<LessonNote> findByLessonIdAndUserIdOrderByTimestampSecAsc(
            UUID lessonId, UUID userId);
}
