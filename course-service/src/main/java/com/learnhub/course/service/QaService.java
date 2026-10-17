package com.learnhub.course.service;

import com.learnhub.common.dto.PageResponse;
import com.learnhub.common.exception.ForbiddenException;
import com.learnhub.common.exception.ResourceNotFoundException;
import com.learnhub.course.dto.request.CreateQaAnswerRequest;
import com.learnhub.course.dto.request.CreateQaQuestionRequest;
import com.learnhub.course.dto.response.QaAnswerResponse;
import com.learnhub.course.dto.response.QaQuestionResponse;
import com.learnhub.course.entity.Lesson;
import com.learnhub.course.entity.QaAnswer;
import com.learnhub.course.entity.QaQuestion;
import com.learnhub.course.repository.LessonRepository;
import com.learnhub.course.repository.QaAnswerRepository;
import com.learnhub.course.repository.QaQuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QaService {

    private final QaQuestionRepository questionRepository;
    private final QaAnswerRepository answerRepository;
    private final LessonRepository lessonRepository;

    // ─── Questions ────────────────────────────────────────────

    /**
     * A student asks a question on a lesson.
     * Requires: already enrolled in the course (checked at the API Gateway or middleware).
     */
    @Transactional
    public QaQuestionResponse createQuestion(UUID lessonId,
                                             CreateQaQuestionRequest request,
                                             UUID userId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "LESSON_NOT_FOUND", "Bài học không tồn tại"));

        QaQuestion question = QaQuestion.builder()
                .lesson(lesson)
                .courseId(lesson.getCourseId())
                .userId(userId)
                .content(request.getContent())
                .build();

        return toQuestionResponse(questionRepository.save(question));
    }

    /**
     * Gets the list of questions for a lesson, including their answers.
     */
    @Transactional(readOnly = true)
    public PageResponse<QaQuestionResponse> getQuestionsByLesson(
            UUID lessonId, Pageable pageable) {
        return PageResponse.of(
                questionRepository.findByLessonIdOrderByCreatedAtDesc(lessonId, pageable)
                        .map(this::toQuestionResponse));
    }

    /**
     * Upvotes a question.
     */
    @Transactional
    public QaQuestionResponse upvoteQuestion(UUID questionId, UUID userId) {
        QaQuestion question = findQuestion(questionId);
        question.setUpvoteCount(question.getUpvoteCount() + 1);
        return toQuestionResponse(questionRepository.save(question));
    }

    /**
     * Marks a question as resolved.
     */
    @Transactional
    public QaQuestionResponse resolveQuestion(UUID questionId, UUID userId) {
        QaQuestion question = findQuestion(questionId);

        // Only the person who asked can mark it as resolved
        if (!question.getUserId().equals(userId)) {
            throw new ForbiddenException("NOT_QUESTION_OWNER",
                    "Bạn không có quyền đánh dấu câu hỏi này");
        }

        question.setResolved(true);
        return toQuestionResponse(questionRepository.save(question));
    }

    // ─── Answers ──────────────────────────────────────────────

    /**
     * Answers a question.
     * is_instructor = true if the answerer is the instructor of that course.
     */
    @Transactional
    public QaAnswerResponse createAnswer(UUID questionId,
                                         CreateQaAnswerRequest request,
                                         UUID userId, boolean isInstructor) {
        QaQuestion question = findQuestion(questionId);

        QaAnswer answer = QaAnswer.builder()
                .question(question)
                .userId(userId)
                .content(request.getContent())
                .isInstructor(isInstructor)
                .build();

        QaAnswer saved = answerRepository.save(answer);
        log.info("Answer created for question: {} by user: {}", questionId, userId);

        return toAnswerResponse(saved);
    }

    /**
     * Marks an answer as accepted (done by the person who asked).
     */
    @Transactional
    public QaAnswerResponse acceptAnswer(UUID questionId, UUID answerId, UUID userId) {
        QaQuestion question = findQuestion(questionId);

        if (!question.getUserId().equals(userId)) {
            throw new ForbiddenException("NOT_QUESTION_OWNER",
                    "Chỉ người hỏi mới được chấp nhận câu trả lời");
        }

        QaAnswer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ANSWER_NOT_FOUND", "Câu trả lời không tồn tại"));

        // Clear accepted flag from all previous answers
        answerRepository.clearAcceptedByQuestionId(questionId);

        answer.setAccepted(true);
        question.setResolved(true);
        questionRepository.save(question);

        return toAnswerResponse(answerRepository.save(answer));
    }

    /**
     * Upvotes an answer.
     */
    @Transactional
    public QaAnswerResponse upvoteAnswer(UUID answerId) {
        QaAnswer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ANSWER_NOT_FOUND", "Câu trả lời không tồn tại"));
        answer.setUpvoteCount(answer.getUpvoteCount() + 1);
        return toAnswerResponse(answerRepository.save(answer));
    }

    // ─── Private helpers ──────────────────────────────────────

    private QaQuestion findQuestion(UUID questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "QUESTION_NOT_FOUND", "Câu hỏi không tồn tại"));
    }

    public QaQuestionResponse toQuestionResponse(QaQuestion question) {
        return QaQuestionResponse.builder()
                .id(question.getId())
                .lessonId(question.getLesson().getId())
                .userId(question.getUserId())
                .content(question.getContent())
                .upvoteCount(question.getUpvoteCount())
                .isResolved(question.isResolved())
                .createdAt(question.getCreatedAt())
                .answers(question.getAnswers().stream()
                        .map(this::toAnswerResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    public QaAnswerResponse toAnswerResponse(QaAnswer answer) {
        return QaAnswerResponse.builder()
                .id(answer.getId())
                .questionId(answer.getQuestion().getId())
                .userId(answer.getUserId())
                .content(answer.getContent())
                .isInstructor(answer.isInstructor())
                .isAccepted(answer.isAccepted())
                .upvoteCount(answer.getUpvoteCount())
                .createdAt(answer.getCreatedAt())
                .build();
    }
}