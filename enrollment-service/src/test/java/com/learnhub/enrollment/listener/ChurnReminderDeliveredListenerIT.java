package com.learnhub.enrollment.listener;

import com.learnhub.common.event.ChurnReminderDeliveredEvent;
import com.learnhub.enrollment.AbstractIntegrationTest;
import com.learnhub.enrollment.config.RabbitMQConfig;
import com.learnhub.enrollment.entity.Enrollment;
import com.learnhub.enrollment.repository.EnrollmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("ChurnReminderDeliveredListener Integration Tests")
class ChurnReminderDeliveredListenerIT extends AbstractIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Test
    @DisplayName("Publish churn.reminder_delivered → records the delivery time, an older redelivery does not overwrite it")
    void onReminderDelivered_RecordsLatestDelivery() {
        Enrollment enrollment = enrollmentRepository.save(Enrollment.builder()
                .userId(UUID.randomUUID()).courseId(UUID.randomUUID()).courseTitle("Course").build());
        Instant delivered = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, "churn.reminder_delivered",
                new ChurnReminderDeliveredEvent(enrollment.getId(), delivered));
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, "churn.reminder_delivered",
                new ChurnReminderDeliveredEvent(enrollment.getId(), delivered.minus(Duration.ofDays(7))));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(enrollmentRepository.findById(enrollment.getId()).orElseThrow().getChurnReminderDeliveredAt())
                        .isEqualTo(delivered));
    }
}
