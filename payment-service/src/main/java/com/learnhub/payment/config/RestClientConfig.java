package com.learnhub.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient courseServiceRestClient(
            @Value("${services.course.base-url}") String courseServiceBaseUrl) {
        return RestClient.builder()
                .baseUrl(courseServiceBaseUrl)
                .build();
    }
}