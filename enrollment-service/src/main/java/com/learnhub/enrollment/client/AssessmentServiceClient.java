package com.learnhub.enrollment.client;

import com.learnhub.enrollment.client.dto.QuizFailureCount;
import com.learnhub.enrollment.client.dto.ServiceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AssessmentServiceClient {

    public record UserCourse(UUID userId, UUID courseId) {
    }

    private final RestClient assessmentServiceRestClient;

    /** Failed quiz attempts for each requested (user, course) pair. */
    public List<QuizFailureCount> getFailureCounts(Collection<UserCourse> pairs) {
        if (pairs.isEmpty()) {
            return List.of();
        }
        ServiceResponse<List<QuizFailureCount>> response = assessmentServiceRestClient.post()
                .uri("/api/v1/internal/quizzes/failure-counts")
                .body(Map.of("items", pairs))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return response == null || response.data() == null ? List.of() : response.data();
    }
}
