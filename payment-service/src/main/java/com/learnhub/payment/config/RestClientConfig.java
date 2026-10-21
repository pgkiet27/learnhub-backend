package com.learnhub.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient courseServiceRestClient(
            RestClient.Builder restClientBuilder,
            @Value("${services.course.base-url}") String courseServiceBaseUrl) {
        return restClientBuilder
                .baseUrl(courseServiceBaseUrl)
                .build();
    }
}