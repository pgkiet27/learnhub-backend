package com.learnhub.assessment.repository;

import com.learnhub.assessment.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    List<Quiz> findByCourseIdOrderByCreatedAtAsc(UUID courseId);

    List<Quiz> findByCourseIdAndIsPublishedTrueOrderByCreatedAtAsc(UUID courseId);
}
