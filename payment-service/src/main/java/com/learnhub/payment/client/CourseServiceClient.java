package com.learnhub.payment.client;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.payment.dto.response.CouponValidationResponse;
import com.learnhub.payment.dto.response.CourseInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseServiceClient {

    private final RestClient courseServiceRestClient;

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

    public CouponValidationResponse validateCoupon(String code, UUID courseId, BigDecimal originalPrice) {
        ApiResponse<CouponValidationResponse> response = courseServiceRestClient.get()
                .uri("/api/v1/internal/coupons/validate?code={code}&courseId={courseId}&originalPrice={price}",
                        code, courseId, originalPrice)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() {
                });

        return response.getData();
    }

    public void markCouponUsed(String code) {
        courseServiceRestClient.post()
                .uri("/api/v1/internal/coupons/{code}/use", code)
                .retrieve()
                .toBodilessEntity();
    }
}