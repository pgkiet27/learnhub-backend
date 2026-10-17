package com.learnhub.course;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for all Course Service Integration Tests.
 * <p>
 * Testcontainers automatically:
 * 1. Pulls and starts PostgreSQL + RabbitMQ Docker containers
 * 2. Runs Flyway migrations (V1-V9) on the test PostgreSQL
 * 3. Overrides Spring config to point to these containers
 * 4. Stops and removes containers after all tests finish
 * <p>
 * All Integration Tests extend this class to reuse the containers
 * (Reusing containers → no start/stop per test class → faster)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    // PostgreSQL container — shared across all test classes (static)
    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("course_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withReuse(true);  // Reuse the container across test classes

    // RabbitMQ container
    @Container
    static final RabbitMQContainer rabbitMQ =
            new RabbitMQContainer("rabbitmq:3-management-alpine")
                    .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.clean-on-validation-error", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        // RabbitMQ
        registry.add("spring.rabbitmq.host", rabbitMQ::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
    }
}