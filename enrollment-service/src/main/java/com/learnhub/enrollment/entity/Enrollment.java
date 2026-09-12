package com.learnhub.enrollment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "enrollments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "course_title", nullable = false)
    private String courseTitle;

    @Column(name = "course_thumbnail_url")
    private String courseThumbnailUrl;

    @Column(name = "total_lessons", nullable = false)
    @Builder.Default
    private Integer totalLessons = 0;

    @Column(name = "completed_lessons", nullable = false)
    @Builder.Default
    private Integer completedLessons = 0;

    @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal progressPercent = BigDecimal.ZERO;

    @Column(name = "is_completed", nullable = false)
    @Builder.Default
    private boolean isCompleted = false;

    @Column(name = "enrolled_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant enrolledAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "enrollment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LessonProgress> lessonProgresses = new ArrayList<>();
}