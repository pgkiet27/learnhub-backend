package com.learnhub.enrollment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    // Auto-configured builder carries the observation (tracing) instrumentation
    private final RestClient.Builder restClientBuilder;

    public RestClientConfig(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    @Bean
    public RestClient courseServiceRestClient(
            @Value("${services.course.base-url}") String courseServiceBaseUrl) {
        return restClientBuilder.clone()
                .baseUrl(courseServiceBaseUrl)
                .build();
    }

    @Bean
    public RestClient identityServiceRestClient(
            @Value("${services.identity.base-url}") String baseUrl) {
        return withTimeouts(baseUrl, Duration.ofSeconds(10));
    }

    @Bean
    public RestClient userServiceRestClient(
            @Value("${services.user.base-url}") String baseUrl) {
        return withTimeouts(baseUrl, Duration.ofSeconds(10));
    }

    @Bean
    public RestClient assessmentServiceRestClient(
            @Value("${services.assessment.base-url}") String baseUrl) {
        return withTimeouts(baseUrl, Duration.ofSeconds(10));
    }

    @Bean
    public RestClient aiServiceRestClient(
            @Value("${services.ai.base-url}") String baseUrl) {
        return withTimeouts(baseUrl, Duration.ofSeconds(60));
    }

    private RestClient withTimeouts(String baseUrl, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(readTimeout);
        return restClientBuilder.clone()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
