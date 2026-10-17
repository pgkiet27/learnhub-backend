package com.learnhub.notification.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Looks up recipients: email lives in identity-service, display name and email preferences in user-service.
 */
@Slf4j
@Component
public class UserDirectoryClient {

    public record UserActivity(UUID userId, String email) {
    }

    public record UserSummary(UUID userId, String fullName, boolean emailLearningReminder) {
    }

    record ServiceResponse<T>(boolean success, String message, T data) {
    }

    private final RestClient identityClient;
    private final RestClient userClient;

    public UserDirectoryClient(@Value("${services.identity.base-url}") String identityUrl,
                               @Value("${services.user.base-url}") String userUrl) {
        this.identityClient = build(identityUrl);
        this.userClient = build(userUrl);
    }

    public Optional<String> findEmail(UUID userId) {
        ServiceResponse<List<UserActivity>> response = identityClient.post()
                .uri("/api/v1/internal/users/activity")
                .body(Map.of("userIds", List.of(userId)))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return firstOf(response).map(UserActivity::email);
    }

    public Optional<UserSummary> findSummary(UUID userId) {
        ServiceResponse<List<UserSummary>> response = userClient.post()
                .uri("/api/v1/internal/users/summaries")
                .body(Map.of("userIds", List.of(userId)))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return firstOf(response);
    }

    private static <T> Optional<T> firstOf(ServiceResponse<List<T>> response) {
        if (response == null || response.data() == null || response.data().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(response.data().getFirst());
    }

    private static RestClient build(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
