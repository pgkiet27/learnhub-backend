package com.learnhub.enrollment.client;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.enrollment.dto.response.CourseInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseServiceClient {

    private final RestClient courseServiceRestClient;

    /**
     * Calls Course Service to verify the course exists and is published.
     * Returns an empty Optional if not found (404) — does NOT throw an exception,
     * leaving the caller to decide the error message appropriate to its own context.
     */
    public Optional<CourseInfoResponse> getCourse(UUID courseId) {
        try {
            ApiResponse<CourseInfoResponse> response = courseServiceRestClient.get()
                    .uri("/api/v1/internal/courses/{courseId}", courseId)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {
                    });

            return Optional.ofNullable(response).map(ApiResponse::getData);

        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();

        } catch (Exception e) {
            log.error("Failed to call Course Service to verify course {}: {}",
                    courseId, e.getMessage());
            throw e;
        }
    }
}