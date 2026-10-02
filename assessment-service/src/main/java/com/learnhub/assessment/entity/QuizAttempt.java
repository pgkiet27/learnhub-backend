package com.learnhub.assessment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quiz_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "is_passed", nullable = false)
    private boolean isPassed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Snapshot answers;

    @Column(name = "time_spent_sec")
    private Integer timeSpentSec;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "submitted_at", nullable = false)
    @Builder.Default
    private Instant submittedAt = Instant.now();

    /** Questions as they were when graded, so the result page still works after the quiz is edited. */
    public record Snapshot(String quizTitle, List<AnsweredQuestion> questions) {
    }

    public record AnsweredQuestion(UUID questionId, String content, String explanation,
                                   List<OptionSnapshot> options, UUID selectedOptionId, UUID correctOptionId) {
    }

    public record OptionSnapshot(UUID id, String content) {
    }
}
