package com.learnhub.identity.event;

import com.learnhub.common.event.UserRegisteredEvent;
import com.learnhub.identity.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes UserRegisteredEvent to RabbitMQ only after the DB transaction that
 * created the user has committed successfully.
 *
 * AuthService.syncUser() publishes this event through Spring's ApplicationEventPublisher
 * (not RabbitTemplate directly) so that a RabbitMQ outage can no longer roll back the
 * user creation itself — the DB write and the broker publish are decoupled on purpose.
 * If RabbitMQ is down at commit time, this listener throws and the event is lost (no
 * outbox/retry here); the user row itself is safely committed regardless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredEventListener {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                "user_registered",
                event
        );
        log.info("Published UserRegisteredEvent for user {}", event.getEmail());
    }
}
