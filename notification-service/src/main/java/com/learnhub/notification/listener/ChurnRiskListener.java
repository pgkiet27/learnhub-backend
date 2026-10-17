package com.learnhub.notification.listener;

import com.learnhub.common.event.ChurnReminderDeliveredEvent;
import com.learnhub.common.event.ChurnRiskDetectedEvent;
import com.learnhub.notification.config.RabbitMQConfig;
import com.learnhub.notification.service.ChurnReminderService;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChurnRiskListener {

    private final ChurnReminderService churnReminderService;
    private final RabbitTemplate rabbitTemplate;

    // Failures are retried a few times (spring.rabbitmq.listener.simple.retry), then dropped
    @RabbitListener(queues = RabbitMQConfig.QUEUE_CHURN_HIGH_RISK)
    public void onChurnRiskDetected(ChurnRiskDetectedEvent event) throws MessagingException {
        log.info("Received churn.high_risk for enrollment {}", event.getEnrollmentId());
        if (churnReminderService.sendReminder(event)) {
            confirmDelivery(event);
        }
    }

    private void confirmDelivery(ChurnRiskDetectedEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_REMINDER_DELIVERED,
                    new ChurnReminderDeliveredEvent(event.getEnrollmentId(), Instant.now()));
        } catch (AmqpException ex) {
            // Not rethrown: a retry would send the email a second time
            log.error("Could not confirm churn reminder for enrollment {}: {}", event.getEnrollmentId(), ex.getMessage());
        }
    }
}
