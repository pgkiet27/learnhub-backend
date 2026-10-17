package com.learnhub.course.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    // exchange used by all services
    public static final String EXCHANGE_NAME = "learnhub.events";
    public static final String QUEUE_ENROLLMENT_CREATED = "course.service.enrollment.created";

    // exchange
    @Bean
    public TopicExchange learnhubExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_NAME).durable(true).build();
    }

    // message converter
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // Course Service publishes these events:
    // - course.approved  → consumed by Notification Service
    // - course.published → consumed by Search/Recommendation Service (Phase 2)

    // Course Service consumes:
    // - enrollment.created → updates total_students
    // - payment.success    → (if needed)

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean
    public Queue enrollmentCreatedQueue() {
        return QueueBuilder.durable(QUEUE_ENROLLMENT_CREATED).build();
    }

    @Bean
    public Binding enrollmentCreatedBinding(Queue enrollmentCreatedQueue,
                                            TopicExchange learnhubExchange) {
        return BindingBuilder
                .bind(enrollmentCreatedQueue)
                .to(learnhubExchange)
                .with("enrollment.created");
    }
}
