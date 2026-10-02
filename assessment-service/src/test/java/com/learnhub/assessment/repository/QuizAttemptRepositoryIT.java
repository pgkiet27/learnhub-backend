package com.learnhub.assessment.repository;

import com.learnhub.assessment.entity.Question;
import com.learnhub.assessment.entity.QuestionOption;
import com.learnhub.assessment.entity.Quiz;
import com.learnhub.assessment.entity.QuizAttempt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
class QuizAttemptRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("assessment_test").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private QuizRepository quizRepository;
    @Autowired
    private QuizAttemptRepository attemptRepository;

    @Test
    void savesSnapshotAsJsonAndCountsFailuresPerUserAndCourse() {
        UUID courseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Quiz quiz = Quiz.builder().courseId(courseId).title("Quiz").createdBy(UUID.randomUUID()).build();
        Question question = Question.builder().quiz(quiz).content("2 + 2?").build();
        question.getOptions().add(QuestionOption.builder().question(question).content("4").isCorrect(true).build());
        quiz.getQuestions().add(question);
        quizRepository.saveAndFlush(quiz);

        QuizAttempt.Snapshot snapshot = new QuizAttempt.Snapshot("Quiz", List.of(new QuizAttempt.AnsweredQuestion(
                question.getId(), "2 + 2?", null, List.of(new QuizAttempt.OptionSnapshot(UUID.randomUUID(), "4")),
                null, UUID.randomUUID())));
        for (boolean passed : new boolean[]{false, false, true}) {
            attemptRepository.save(QuizAttempt.builder().quiz(quiz).courseId(courseId).userId(userId)
                    .score(passed ? new BigDecimal("100") : BigDecimal.ZERO).isPassed(passed)
                    .answers(snapshot).startedAt(Instant.now()).build());
        }
        attemptRepository.flush();

        QuizAttempt loaded = attemptRepository.findByCourseIdAndUserId(courseId, userId).getFirst();
        assertThat(loaded.getAnswers().questions().getFirst().content()).isEqualTo("2 + 2?");

        List<Object[]> failures = attemptRepository.countFailures(List.of(userId), List.of(courseId));
        assertThat(failures).hasSize(1);
        assertThat(((Number) failures.getFirst()[2]).longValue()).isEqualTo(2);
    }
}
