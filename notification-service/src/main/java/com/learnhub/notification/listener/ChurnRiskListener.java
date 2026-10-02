package com.learnhub.notification.listener;

import com.learnhub.common.event.ChurnRiskDetectedEvent;
import com.learnhub.notification.config.RabbitMQConfig;
import com.learnhub.notification.service.ChurnReminderService;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChurnRiskListener {

    private final ChurnReminderService churnReminderService;

    // Failures are retried a few times (spring.rabbitmq.listener.simple.retry), then dropped
    @RabbitListener(queues = RabbitMQConfig.QUEUE_CHURN_HIGH_RISK)
    public void onChurnRiskDetected(ChurnRiskDetectedEvent event) throws MessagingException {
        log.info("Received churn.high_risk for enrollment {}", event.getEnrollmentId());
        churnReminderService.sendReminder(event);
    }
}
