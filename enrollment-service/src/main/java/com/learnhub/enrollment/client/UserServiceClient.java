package com.learnhub.enrollment.client;

import com.learnhub.enrollment.client.dto.ServiceResponse;
import com.learnhub.enrollment.client.dto.SupportTicketCount;
import com.learnhub.enrollment.client.dto.UserSummary;
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
public class UserServiceClient {

    private final RestClient userServiceRestClient;

    public Map<UUID, UserSummary> getSummaries(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        ServiceResponse<List<UserSummary>> response = userServiceRestClient.post()
                .uri("/api/v1/internal/users/summaries")
                .body(Map.of("userIds", userIds))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        if (response == null || response.data() == null) {
            return Map.of();
        }
        return response.data().stream()
                .collect(Collectors.toMap(UserSummary::userId, Function.identity()));
    }

    /** Support tickets each user opened in the last 30 days. */
    public Map<UUID, Long> getSupportTicketCounts(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        ServiceResponse<List<SupportTicketCount>> response = userServiceRestClient.post()
                .uri("/api/v1/internal/users/support-ticket-counts")
                .body(Map.of("userIds", userIds))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        if (response == null || response.data() == null) {
            return Map.of();
        }
        return response.data().stream()
                .collect(Collectors.toMap(SupportTicketCount::userId, SupportTicketCount::ticketsLast30Days));
    }
}
