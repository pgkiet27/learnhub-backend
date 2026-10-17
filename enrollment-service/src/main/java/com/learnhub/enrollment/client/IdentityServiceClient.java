package com.learnhub.enrollment.client;

import com.learnhub.enrollment.client.dto.ServiceResponse;
import com.learnhub.enrollment.client.dto.UserActivity;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class IdentityServiceClient {

    private final RestClient identityServiceRestClient;

    public Map<UUID, UserActivity> getActivity(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        ServiceResponse<List<UserActivity>> response = identityServiceRestClient.post()
                .uri("/api/v1/internal/users/activity")
                .body(Map.of("userIds", userIds))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        if (response == null || response.data() == null) {
            return Map.of();
        }
        return response.data().stream()
                .collect(Collectors.toMap(UserActivity::userId, Function.identity()));
    }
}
