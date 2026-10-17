package com.learnhub.user.service;

import com.learnhub.user.dto.response.UserSummaryResponse;
import com.learnhub.user.entity.NotificationSettings;
import com.learnhub.user.entity.UserProfile;
import com.learnhub.user.repository.NotificationSettingsRepository;
import com.learnhub.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserSummaryService {
    private final UserProfileRepository userProfileRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> getSummaries(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, UserProfile> profiles = userProfileRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, Function.identity()));
        Map<UUID, NotificationSettings> settings = notificationSettingsRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(NotificationSettings::getUserId, Function.identity()));

        return userIds.stream().distinct()
                .map(id -> {
                    UserProfile profile = profiles.get(id);
                    NotificationSettings s = settings.get(id);
                    return UserSummaryResponse.builder()
                            .userId(id)
                            .fullName(profile != null ? profile.getFullname() : "")
                            .avatarUrl(profile != null ? profile.getAvatarUrl() : null)
                            // no settings row yet = defaults, where reminders are on
                            .emailLearningReminder(s == null || s.isEmailLearningReminder())
                            .build();
                })
                .toList();
    }
}
