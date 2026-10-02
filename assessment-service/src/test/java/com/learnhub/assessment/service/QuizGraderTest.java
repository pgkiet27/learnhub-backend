package com.learnhub.assessment.service;

import com.learnhub.assessment.entity.Question;
import com.learnhub.assessment.entity.QuestionOption;
import com.learnhub.assessment.entity.Quiz;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QuizGraderTest {

    private static QuestionOption option(boolean correct) {
        return QuestionOption.builder().id(UUID.randomUUID()).content("o").isCorrect(correct).build();
    }

    private static Question question(QuestionOption... options) {
        return Question.builder().id(UUID.randomUUID()).content("q").options(List.of(options)).build();
    }

    private static Quiz quiz(int passScore, Question... questions) {
        return Quiz.builder().passScore((short) passScore).questions(List.of(questions)).build();
    }

    @Test
    @DisplayName("grade - 2 of 3 correct with pass score 60 - 66.67%, passed")
    void grade_Passed() {
        QuestionOption a1 = option(true), a2 = option(false);
        QuestionOption b1 = option(false), b2 = option(true);
        QuestionOption c1 = option(true), c2 = option(false);
        Quiz quiz = quiz(60, question(a1, a2), question(b1, b2), question(c1, c2));

        QuizGrader.Grade grade = QuizGrader.grade(quiz, Map.of(
                quiz.getQuestions().get(0).getId(), a1.getId(),
                quiz.getQuestions().get(1).getId(), b1.getId(),
                quiz.getQuestions().get(2).getId(), c1.getId()));

        assertThat(grade.score()).isEqualByComparingTo(new BigDecimal("66.67"));
        assertThat(grade.correctCount()).isEqualTo(2);
        assertThat(grade.passed()).isTrue();
        assertThat(grade.answers().get(1).correctOptionId()).isEqualTo(b2.getId());
    }

    @Test
    @DisplayName("grade - unanswered and foreign option ids count as wrong")
    void grade_UnansweredAndForeignOption() {
        QuestionOption a1 = option(true), a2 = option(false);
        QuestionOption b1 = option(true), b2 = option(false);
        Quiz quiz = quiz(50, question(a1, a2), question(b1, b2));

        // answers question 1 with an option that belongs to question 2
        QuizGrader.Grade grade = QuizGrader.grade(quiz, Map.of(quiz.getQuestions().get(0).getId(), b1.getId()));

        assertThat(grade.score()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(grade.passed()).isFalse();
        assertThat(grade.answers().get(0).selectedOptionId()).isNull();
    }
}
