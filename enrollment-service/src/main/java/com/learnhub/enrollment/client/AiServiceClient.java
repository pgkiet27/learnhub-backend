package com.learnhub.enrollment.client;

import com.learnhub.enrollment.client.dto.ChurnBatchRequest;
import com.learnhub.enrollment.client.dto.ChurnBatchResponse;
import com.learnhub.enrollment.client.dto.ServiceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class AiServiceClient {

    private final RestClient aiServiceRestClient;

    public ChurnBatchResponse predictChurn(ChurnBatchRequest request) {
        ServiceResponse<ChurnBatchResponse> response = aiServiceRestClient.post()
                .uri("/api/v1/internal/ai/churn/predict-batch")
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        if (response == null || response.data() == null) {
            throw new IllegalStateException("ai-service returned an empty churn prediction response");
        }
        return response.data();
    }
}
