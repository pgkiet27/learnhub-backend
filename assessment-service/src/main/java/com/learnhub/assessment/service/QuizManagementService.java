package com.learnhub.assessment.service;

import com.learnhub.assessment.client.CourseServiceClient;
import com.learnhub.assessment.dto.request.QuizRequest;
import com.learnhub.assessment.dto.response.QuizResponses.OptionDetail;
import com.learnhub.assessment.dto.response.QuizResponses.QuestionDetail;
import com.learnhub.assessment.dto.response.QuizResponses.QuizDetail;
import com.learnhub.assessment.dto.response.QuizResponses.QuizSummary;
import com.learnhub.assessment.entity.Question;
import com.learnhub.assessment.entity.QuestionOption;
import com.learnhub.assessment.entity.Quiz;
import com.learnhub.assessment.repository.QuizAttemptRepository;
import com.learnhub.assessment.repository.QuizRepository;
import com.learnhub.common.exception.BadRequestException;
import com.learnhub.common.exception.ConflictException;
import com.learnhub.common.exception.ForbiddenException;
import com.learnhub.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Quiz authoring for the course's instructor (or an admin). */
@Service
@RequiredArgsConstructor
public class QuizManagementService {

    private final QuizRepository quizRepository;
    private final QuizAttemptRepository attemptRepository;
    private final CourseServiceClient courseServiceClient;

    @Transactional(readOnly = true)
    public List<QuizSummary> listForCourse(UUID courseId, UUID userId, String role) {
        requireCourseOwner(courseId, userId, role);
        List<Quiz> quizzes = quizRepository.findByCourseIdOrderByCreatedAtAsc(courseId);

        Map<UUID, long[]> stats = new HashMap<>();
        if (!quizzes.isEmpty()) {
            for (Object[] row : attemptRepository.statsByQuiz(quizzes.stream().map(Quiz::getId).toList())) {
                stats.put((UUID) row[0], new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
            }
        }
        return quizzes.stream().map(q -> {
            long[] s = stats.getOrDefault(q.getId(), new long[]{0, 0});
            Integer passRate = s[0] == 0 ? null : (int) Math.round(s[1] * 100.0 / s[0]);
            return new QuizSummary(q.getId(), q.getCourseId(), q.getLessonId(), q.getTitle(), q.getPassScore(),
                    q.getTimeLimitSec(), q.getMaxAttempts(), q.isPublished(), q.getQuestions().size(),
                    s[0], passRate, q.getUpdatedAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public QuizDetail get(UUID quizId, UUID userId, String role) {
        Quiz quiz = findQuiz(quizId);
        requireCourseOwner(quiz.getCourseId(), userId, role);
        return toDetail(quiz);
    }

    @Transactional
    public QuizDetail create(QuizRequest request, UUID userId, String role) {
        requireCourseOwner(request.getCourseId(), userId, role);
        Quiz quiz = Quiz.builder().courseId(request.getCourseId()).createdBy(userId).build();
        apply(quiz, request);
        return toDetail(quizRepository.save(quiz));
    }

    @Transactional
    public QuizDetail update(UUID quizId, QuizRequest request, UUID userId, String role) {
        Quiz quiz = findQuiz(quizId);
        requireCourseOwner(quiz.getCourseId(), userId, role);
        apply(quiz, request);
        // Managed entity: flushing cascades the persist of the new questions (and assigns their ids)
        quizRepository.flush();
        return toDetail(quiz);
    }

    @Transactional
    public QuizDetail setPublished(UUID quizId, boolean published, UUID userId, String role) {
        Quiz quiz = findQuiz(quizId);
        requireCourseOwner(quiz.getCourseId(), userId, role);
        quiz.setPublished(published);
        return toDetail(quiz);
    }

    @Transactional
    public void delete(UUID quizId, UUID userId, String role) {
        Quiz quiz = findQuiz(quizId);
        requireCourseOwner(quiz.getCourseId(), userId, role);
        if (attemptRepository.existsByQuizId(quizId)) {
            throw new ConflictException("QUIZ_HAS_ATTEMPTS",
                    "Quiz đã có học viên làm bài — hãy ẩn quiz thay vì xóa để giữ lịch sử làm bài");
        }
        quizRepository.delete(quiz);
    }

    private void apply(Quiz quiz, QuizRequest request) {
        for (QuizRequest.QuestionRequest q : request.getQuestions()) {
            long correct = q.getOptions().stream().filter(QuizRequest.OptionRequest::isCorrect).count();
            if (correct != 1) {
                throw new BadRequestException("INVALID_QUESTION",
                        "Mỗi câu hỏi phải có đúng 1 đáp án đúng: \"" + q.getContent() + "\"");
            }
        }
        quiz.setLessonId(request.getLessonId());
        quiz.setTitle(request.getTitle().strip());
        quiz.setDescription(request.getDescription());
        quiz.setPassScore(request.getPassScore().shortValue());
        quiz.setTimeLimitSec(request.getTimeLimitSec());
        quiz.setMaxAttempts(request.getMaxAttempts());

        // Questions are replaced as a whole; past attempts keep their own snapshot
        quiz.getQuestions().clear();
        for (int i = 0; i < request.getQuestions().size(); i++) {
            QuizRequest.QuestionRequest qr = request.getQuestions().get(i);
            Question question = Question.builder()
                    .quiz(quiz).content(qr.getContent().strip()).explanation(qr.getExplanation()).displayOrder(i)
                    .build();
            for (int j = 0; j < qr.getOptions().size(); j++) {
                QuizRequest.OptionRequest or = qr.getOptions().get(j);
                question.getOptions().add(QuestionOption.builder()
                        .question(question).content(or.getContent().strip()).isCorrect(or.isCorrect()).displayOrder(j)
                        .build());
            }
            quiz.getQuestions().add(question);
        }
    }

    private Quiz findQuiz(UUID quizId) {
        return quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("QUIZ_NOT_FOUND", "Quiz không tồn tại"));
    }

    private void requireCourseOwner(UUID courseId, UUID userId, String role) {
        CourseServiceClient.CourseOwner course = courseServiceClient.getOwner(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("COURSE_NOT_FOUND", "Khóa học không tồn tại"));
        if (!"admin".equals(role) && !userId.equals(course.instructorId())) {
            throw new ForbiddenException("NOT_COURSE_OWNER", "Bạn không phải giảng viên của khóa học này");
        }
    }

    private static QuizDetail toDetail(Quiz q) {
        return new QuizDetail(q.getId(), q.getCourseId(), q.getLessonId(), q.getTitle(), q.getDescription(),
                q.getPassScore(), q.getTimeLimitSec(), q.getMaxAttempts(), q.isPublished(),
                q.getQuestions().stream().map(question -> new QuestionDetail(
                        question.getId(), question.getContent(), question.getExplanation(),
                        question.getOptions().stream()
                                .map(o -> new OptionDetail(o.getId(), o.getContent(), o.isCorrect()))
                                .toList()))
                        .toList());
    }
}
