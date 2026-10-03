package com.learnhub.enrollment.listener;

import com.learnhub.common.event.ChurnReminderDeliveredEvent;
import com.learnhub.enrollment.config.RabbitMQConfig;
import com.learnhub.enrollment.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChurnReminderDeliveredListener {

    private final EnrollmentRepository enrollmentRepository;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_CHURN_REMINDER_DELIVERED)
    @Transactional
    public void onReminderDelivered(ChurnReminderDeliveredEvent event) {
        enrollmentRepository.findById(event.getEnrollmentId()).ifPresentOrElse(e -> {
            // Redelivered or out-of-order messages must not move the time backwards
            if (e.getChurnReminderDeliveredAt() == null || event.getDeliveredAt().isAfter(e.getChurnReminderDeliveredAt())) {
                e.setChurnReminderDeliveredAt(event.getDeliveredAt());
            }
        }, () -> log.warn("Churn reminder delivered for unknown enrollment {}", event.getEnrollmentId()));
    }
}
