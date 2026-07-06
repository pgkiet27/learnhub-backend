package com.learnhub.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    // Scan more "common" package to use GlobalExceptionHandler
    scanBasePackages = {"com.learnhub.{pkg}", "com.learnhub.common"}
)
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}