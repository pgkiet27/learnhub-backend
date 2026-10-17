package com.learnhub.course.repository;

import com.learnhub.course.entity.QaQuestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface QaQuestionRepository extends JpaRepository<QaQuestion, UUID> {
    Page<QaQuestion> findByLessonIdOrderByCreatedAtDesc(UUID lessonId, Pageable pageable);

    Page<QaQuestion> findByCourseIdOrderByCreatedAtDesc(UUID courseId, Pageable pageable);
}