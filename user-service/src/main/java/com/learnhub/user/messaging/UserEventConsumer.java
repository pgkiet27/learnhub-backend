package com.learnhub.user.messaging;

import com.learnhub.common.event.UserRegisteredEvent;
import com.learnhub.user.entity.NotificationSettings;
import com.learnhub.user.entity.UserProfile;
import com.learnhub.user.repository.NotificationSettingsRepository;
import com.learnhub.user.repository.UserProfileRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

// listen events related to User from RabbitMQ
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {
    private final UserProfileRepository userProfileRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;

    /*
     * when we have new user registered -> automatically create default profile
     *
     * RabbitMQ queue: user.service.user.registered
     * Routing key: user.registered
     */
    @RabbitListener(queues = "user.service.user.registered")
    @Transactional
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent for: {}", event.getEmail());

        UUID userId = event.getUserId();

        // avoid create duplicate profile (idempotent)
        if(userProfileRepository.existsByUserId(userId)) {
            log.warn("UserProfile already exists for userId: {}", userId);
            return;
        }

        // create default profile
        UserProfile profile = UserProfile.builder()
                .userId(userId)
                .fullname(extractNameFromEmail(event.getEmail()))
                .build();
        userProfileRepository.save(profile);

        // create default notification settings
        NotificationSettings settings = NotificationSettings.builder()
                .userId(userId)
                .build();
        notificationSettingsRepository.save(settings);

        log.info("Created UserProfile and NotificationSettings for: {}", event.getEmail());
    }

    // get name from the head of email (vd: kiet.nguyen@gmail.com -> kiet.nguyen)
    private String extractNameFromEmail(String email) {
        if(email == null || !email.contains("@")) return "";
        return email.split("@")[0].replace(".", "");
    }



}
