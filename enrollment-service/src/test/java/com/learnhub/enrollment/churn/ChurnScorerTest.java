package com.learnhub.enrollment.churn;

import com.learnhub.enrollment.entity.Enrollment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ChurnScorerTest {

    private static final Duration COOLDOWN = Duration.ofDays(7);
    private static final Instant LAST_RUN = Instant.parse("2026-10-02T19:00:00.200Z");

    private static Enrollment remindedAt(Instant at) {
        return Enrollment.builder().churnRemindedAt(at).build();
    }

    @Test
    @DisplayName("reminderDue - next week's run starts a few ms earlier - still due")
    void reminderDue_RunStartsSlightlyEarlier() {
        Instant nextWeek = LAST_RUN.plus(COOLDOWN).minusMillis(150);

        assertThat(ChurnScorer.reminderDue(remindedAt(LAST_RUN), nextWeek, COOLDOWN)).isTrue();
    }

    @Test
    @DisplayName("reminderDue - one day before the cooldown ends - not due")
    void reminderDue_WithinCooldown() {
        Instant sixDaysLater = LAST_RUN.plus(Duration.ofDays(6));

        assertThat(ChurnScorer.reminderDue(remindedAt(LAST_RUN), sixDaysLater, COOLDOWN)).isFalse();
    }

    @Test
    @DisplayName("reminderDue - never reminded - due")
    void reminderDue_NeverReminded() {
        assertThat(ChurnScorer.reminderDue(remindedAt(null), LAST_RUN, COOLDOWN)).isTrue();
    }
}
