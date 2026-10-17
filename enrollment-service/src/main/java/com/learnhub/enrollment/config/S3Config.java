package com.learnhub.enrollment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** Only needed to export the churn dataset; without it the service runs with no AWS credentials. */
@Configuration
@ConditionalOnProperty(name = "churn.dataset.export.enabled", havingValue = "true")
public class S3Config {

    @Value("${aws.region}")
    private String region;

    @Value("${aws.profile:}")
    private String profile;

    @Bean
    public S3Client s3Client() {
        DefaultCredentialsProvider.Builder credentials = DefaultCredentialsProvider.builder();
        if (StringUtils.hasText(profile)) {
            credentials.profileName(profile);
        }
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials.build())
                .build();
    }
}
