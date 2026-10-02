package com.learnhub.assessment.service;

import com.learnhub.assessment.entity.Question;
import com.learnhub.assessment.entity.QuestionOption;
import com.learnhub.assessment.entity.Quiz;
import com.learnhub.assessment.entity.QuizAttempt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Single-answer MCQ grading: score = % of questions answered with the correct option. */
public final class QuizGrader {

    public record Grade(BigDecimal score, boolean passed, int correctCount, List<QuizAttempt.AnsweredQuestion> answers) {
    }

    private QuizGrader() {
    }

    public static Grade grade(Quiz quiz, Map<UUID, UUID> selected) {
        List<QuizAttempt.AnsweredQuestion> answers = quiz.getQuestions().stream()
                .map(q -> answer(q, selected.get(q.getId())))
                .toList();
        int correct = (int) answers.stream()
                .filter(a -> a.selectedOptionId() != null && a.selectedOptionId().equals(a.correctOptionId()))
                .count();
        BigDecimal score = answers.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(correct * 100L).divide(BigDecimal.valueOf(answers.size()), 2, RoundingMode.HALF_UP);
        boolean passed = score.compareTo(BigDecimal.valueOf(quiz.getPassScore())) >= 0;
        return new Grade(score, passed, correct, answers);
    }

    private static QuizAttempt.AnsweredQuestion answer(Question q, UUID selectedOptionId) {
        UUID correctId = q.getOptions().stream()
                .filter(QuestionOption::isCorrect)
                .map(QuestionOption::getId)
                .findFirst().orElse(null);
        // An option id from another question counts as no answer
        boolean belongs = selectedOptionId != null
                && q.getOptions().stream().anyMatch(o -> Objects.equals(o.getId(), selectedOptionId));
        return new QuizAttempt.AnsweredQuestion(
                q.getId(), q.getContent(), q.getExplanation(),
                q.getOptions().stream().map(o -> new QuizAttempt.OptionSnapshot(o.getId(), o.getContent())).toList(),
                belongs ? selectedOptionId : null,
                correctId);
    }
}
