package com.learnhub.assessment.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.UUID;

@Component
public class EnrollmentServiceClient {

    record EnrollmentStatus(boolean enrolled) {
    }

    record ServiceResponse<T>(boolean success, String message, T data) {
    }

    private final RestClient restClient;

    public EnrollmentServiceClient(@Value("${services.enrollment.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public boolean isEnrolled(UUID userId, UUID courseId) {
        // Called directly (not through the gateway), passing the caller's identity like the gateway would
        ServiceResponse<EnrollmentStatus> response = restClient.get()
                .uri("/api/v1/enrollments/{courseId}/status", courseId)
                .header("X-User-Id", userId.toString())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return response != null && response.data() != null && response.data().enrolled();
    }
}
