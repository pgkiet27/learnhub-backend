package com.learnhub.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "email_learning_reminder", nullable = false)
    @Builder.Default
    private boolean emailLearningReminder = true;

    @Column(name = "email_promotions", nullable = false)
    @Builder.Default
    private boolean emailPromotions = true;

    @Column(name = "email_new_course", nullable = false)
    @Builder.Default
    private boolean emailNewCourse = true;

    @Column(name = "email_qa_answered", nullable = false)
    @Builder.Default
    private boolean emailQaAnswered = true;

    @Column(name = "push_enabled", nullable = false)
    @Builder.Default
    private boolean pushEnabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
