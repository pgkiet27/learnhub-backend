package com.learnhub.course;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    scanBasePackages = {"com.learnhub.course", "com.learnhub.common"}
)
public class CourseServiceApplication {
    public static void main(String[] agrs) {
        SpringApplication.run(CourseServiceApplication.class, agrs);
    }
}
