package com.learnhub.assessment.repository;

import com.learnhub.assessment.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

    long countByQuizIdAndUserId(UUID quizId, UUID userId);

    boolean existsByQuizId(UUID quizId);

    List<QuizAttempt> findByCourseIdAndUserId(UUID courseId, UUID userId);

    /** Rows: [quiz_id, attempts, passed attempts]. */
    @Query("""
            SELECT a.quiz.id, COUNT(a), SUM(CASE WHEN a.isPassed = true THEN 1 ELSE 0 END)
            FROM QuizAttempt a WHERE a.quiz.id IN :quizIds GROUP BY a.quiz.id
            """)
    List<Object[]> statsByQuiz(Collection<UUID> quizIds);

    /** Rows: [user_id, course_id, failed attempts] for the given users and courses. */
    @Query("""
            SELECT a.userId, a.courseId, COUNT(a) FROM QuizAttempt a
            WHERE a.isPassed = false AND a.userId IN :userIds AND a.courseId IN :courseIds
            GROUP BY a.userId, a.courseId
            """)
    List<Object[]> countFailures(Collection<UUID> userIds, Collection<UUID> courseIds);
}
