package com.learnhub.identity.controller;

import com.learnhub.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Controller đơn giản để verify service đang chạy.
 * Xóa hoặc giữ lại tùy ý sau khi implement đầy đủ.
 */
@Tag(name = "Health", description = "Service health check endpoints")
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    // Inject giá trị từ application.yml
    @Value("${spring.application.name}")
    private String serviceName;

    @Operation(summary = "Check service status")
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> data = Map.of(
                "service", serviceName,
                "status", "UP",
                "timestamp", Instant.now().toString()
        );
        return ResponseEntity.ok(ApiResponse.success(data, "Service is running"));
    }
}