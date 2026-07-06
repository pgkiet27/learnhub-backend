package com.learnhub.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    scanBasePackages = {
        "com.learnhub.identity",  // scan all package for this service
        "com.learnhub.common"     // Scan common to use GlobalExceptionHandler
    }
)
@EnableScheduling  // turn on feature: scheduled tasks (cleanup expired tokens)
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}