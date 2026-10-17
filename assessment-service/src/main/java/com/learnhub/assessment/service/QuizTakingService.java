package com.learnhub.assessment.service;

import com.learnhub.assessment.client.EnrollmentServiceClient;
import com.learnhub.assessment.dto.request.SubmitAttemptRequest;
import com.learnhub.assessment.dto.response.QuizResponses.AttemptResult;
import com.learnhub.assessment.dto.response.QuizResponses.OptionToTake;
import com.learnhub.assessment.dto.response.QuizResponses.QuestionToTake;
import com.learnhub.assessment.dto.response.QuizResponses.QuizToTake;
import com.learnhub.assessment.dto.response.QuizResponses.ResultQuestion;
import com.learnhub.assessment.dto.response.QuizResponses.StudentQuizItem;
import com.learnhub.assessment.entity.Quiz;
import com.learnhub.assessment.entity.QuizAttempt;
import com.learnhub.assessment.repository.QuizAttemptRepository;
import com.learnhub.assessment.repository.QuizRepository;
import com.learnhub.common.exception.ConflictException;
import com.learnhub.common.exception.ForbiddenException;
import com.learnhub.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** Quizzes from the student's side; every call requires being enrolled in the course. */
@Service
@RequiredArgsConstructor
public class QuizTakingService {

    private final QuizRepository quizRepository;
    private final QuizAttemptRepository attemptRepository;
    private final EnrollmentServiceClient enrollmentServiceClient;

    @Transactional(readOnly = true)
    public List<StudentQuizItem> listForCourse(UUID courseId, UUID userId, String role) {
        requireEnrolled(courseId, userId, role);
        Map<UUID, List<QuizAttempt>> mine = attemptRepository.findByCourseIdAndUserId(courseId, userId).stream()
                .collect(Collectors.groupingBy(a -> a.getQuiz().getId()));

        return quizRepository.findByCourseIdAndIsPublishedTrueOrderByCreatedAtAsc(courseId).stream()
                .map(q -> {
                    List<QuizAttempt> attempts = mine.getOrDefault(q.getId(), List.of());
                    BigDecimal best = attempts.stream().map(QuizAttempt::getScore)
                            .max(Comparator.naturalOrder()).orElse(null);
                    return new StudentQuizItem(q.getId(), q.getLessonId(), q.getTitle(), q.getQuestions().size(),
                            q.getPassScore(), q.getTimeLimitSec(), q.getMaxAttempts(), attempts.size(), best,
                            attempts.stream().anyMatch(QuizAttempt::isPassed));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public QuizToTake getForTaking(UUID quizId, UUID userId, String role) {
        Quiz quiz = findPublished(quizId);
        requireEnrolled(quiz.getCourseId(), userId, role);
        return new QuizToTake(quiz.getId(), quiz.getCourseId(), quiz.getTitle(), quiz.getDescription(),
                quiz.getPassScore(), quiz.getTimeLimitSec(), quiz.getMaxAttempts(),
                attemptRepository.countByQuizIdAndUserId(quizId, userId),
                quiz.getQuestions().stream().map(q -> new QuestionToTake(q.getId(), q.getContent(),
                        q.getOptions().stream().map(o -> new OptionToTake(o.getId(), o.getContent())).toList()))
                        .toList());
    }

    @Transactional
    public AttemptResult submit(UUID quizId, UUID userId, String role, SubmitAttemptRequest request) {
        Quiz quiz = findPublished(quizId);
        requireEnrolled(quiz.getCourseId(), userId, role);

        long used = attemptRepository.countByQuizIdAndUserId(quizId, userId);
        if (quiz.getMaxAttempts() != null && used >= quiz.getMaxAttempts()) {
            throw new ConflictException("MAX_ATTEMPTS_REACHED",
                    "Bạn đã dùng hết " + quiz.getMaxAttempts() + " lượt làm bài của quiz này");
        }

        QuizGrader.Grade grade = QuizGrader.grade(quiz, request.getAnswers());
        Instant now = Instant.now();
        Integer spent = request.getTimeSpentSec();
        QuizAttempt attempt = attemptRepository.save(QuizAttempt.builder()
                .quiz(quiz)
                .courseId(quiz.getCourseId())
                .userId(userId)
                .score(grade.score())
                .isPassed(grade.passed())
                .answers(new QuizAttempt.Snapshot(quiz.getTitle(), grade.answers()))
                .timeSpentSec(spent)
                .startedAt(spent == null ? now : now.minusSeconds(spent))
                .submittedAt(now)
                .build());
        return toResult(attempt, used + 1);
    }

    @Transactional(readOnly = true)
    public AttemptResult getResult(UUID attemptId, UUID userId, String role) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .filter(a -> a.getUserId().equals(userId) || "admin".equals(role))
                .orElseThrow(() -> new ResourceNotFoundException("ATTEMPT_NOT_FOUND", "Không tìm thấy bài làm"));
        return toResult(attempt, attemptRepository.countByQuizIdAndUserId(attempt.getQuiz().getId(), attempt.getUserId()));
    }

    private void requireEnrolled(UUID courseId, UUID userId, String role) {
        if (!"admin".equals(role) && !enrollmentServiceClient.isEnrolled(userId, courseId)) {
            throw new ForbiddenException("NOT_ENROLLED", "Bạn cần đăng ký khóa học để làm quiz");
        }
    }

    private Quiz findPublished(UUID quizId) {
        return quizRepository.findById(quizId)
                .filter(Quiz::isPublished)
                .orElseThrow(() -> new ResourceNotFoundException("QUIZ_NOT_FOUND", "Quiz không tồn tại"));
    }

    private static AttemptResult toResult(QuizAttempt a, long attemptsUsed) {
        Quiz quiz = a.getQuiz();
        List<ResultQuestion> questions = a.getAnswers().questions().stream()
                .map(q -> new ResultQuestion(q.questionId(), q.content(), q.explanation(),
                        q.options().stream().map(o -> new OptionToTake(o.id(), o.content())).toList(),
                        q.selectedOptionId(), q.correctOptionId(),
                        q.selectedOptionId() != null && Objects.equals(q.selectedOptionId(), q.correctOptionId())))
                .toList();
        int correct = (int) questions.stream().filter(ResultQuestion::correct).count();
        return new AttemptResult(a.getId(), quiz.getId(), a.getCourseId(), a.getAnswers().quizTitle(), a.getScore(),
                a.isPassed(), quiz.getPassScore(), correct, questions.size(), a.getTimeSpentSec(), a.getSubmittedAt(),
                attemptsUsed, quiz.getMaxAttempts(), questions);
    }
}
