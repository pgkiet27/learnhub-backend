package com.learnhub.assessment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response shapes of the quiz API, grouped in one place. */
public final class QuizResponses {

    private QuizResponses() {
    }

    /** Instructor list row. */
    public record QuizSummary(
            UUID id, UUID courseId, UUID lessonId, String title, int passScore, Integer timeLimitSec,
            Integer maxAttempts, boolean published, int questionCount, long attemptCount,
            Integer passRate, Instant updatedAt) {
    }

    /** Instructor editor view, including correct answers. */
    public record QuizDetail(
            UUID id, UUID courseId, UUID lessonId, String title, String description, int passScore,
            Integer timeLimitSec, Integer maxAttempts, boolean published, List<QuestionDetail> questions) {
    }

    public record QuestionDetail(UUID id, String content, String explanation, List<OptionDetail> options) {
    }

    public record OptionDetail(UUID id, String content, boolean correct) {
    }

    /** Student list row, with the student's own progress on it. */
    public record StudentQuizItem(
            UUID id, UUID lessonId, String title, int questionCount, int passScore, Integer timeLimitSec,
            Integer maxAttempts, long attemptsUsed, BigDecimal bestScore, boolean passed) {
    }

    /** Quiz as shown to a student taking it — no correct answers. */
    public record QuizToTake(
            UUID id, UUID courseId, String title, String description, int passScore, Integer timeLimitSec,
            Integer maxAttempts, long attemptsUsed, List<QuestionToTake> questions) {
    }

    public record QuestionToTake(UUID id, String content, List<OptionToTake> options) {
    }

    public record OptionToTake(UUID id, String content) {
    }

    public record AttemptResult(
            UUID attemptId, UUID quizId, UUID courseId, String quizTitle, BigDecimal score, boolean passed,
            int passScore, int correctCount, int totalQuestions, Integer timeSpentSec, Instant submittedAt,
            long attemptsUsed, Integer maxAttempts, List<ResultQuestion> questions) {
    }

    public record ResultQuestion(
            UUID id, String content, String explanation, List<OptionToTake> options,
            UUID selectedOptionId, UUID correctOptionId, boolean correct) {
    }
}
