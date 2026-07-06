package com.learnhub.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    scanBasePackages = {"com.learnhub.user", "com.learnhub.common"}
)
public class UserServiceApplication {
    public static void main(String[] agrs) {
        SpringApplication.run(UserServiceApplication.class, agrs);
    }
}
