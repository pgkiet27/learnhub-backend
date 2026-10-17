package com.learnhub.assessment.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
public class CourseServiceClient {

    public record CourseOwner(UUID id, String title, UUID instructorId, String status) {
    }

    record ServiceResponse<T>(boolean success, String message, T data) {
    }

    private final RestClient restClient;

    public CourseServiceClient(@Value("${services.course.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** Owner of the course in any status; empty if the course does not exist. */
    public Optional<CourseOwner> getOwner(UUID courseId) {
        try {
            ServiceResponse<CourseOwner> response = restClient.get()
                    .uri("/api/v1/internal/courses/{courseId}/owner", courseId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return Optional.ofNullable(response).map(ServiceResponse::data);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
