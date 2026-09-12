package com.learnhub.enrollment.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    // exchange use for all services
    public static final String EXCHANGE_NAME = "learnhub.events";
    public static final String QUEUE_PAYMENT_SUCCESS = "enrollment.service.payment.success";

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

    // Enrollment Service publish:
    // - enrollment.created → consumed by Course Service (updates total_students, B3 Part 3)
    //                      → later consumed by Notification Service (sends welcome email, B7)
    // - course.completed   → later consumed by Certificate Service (auto-issues certificate, B6)

    // Enrollment Service consume:
    // - payment.success    → auto-enroll into a paid course (published by Payment Service in B5)
    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean
    public Queue paymentSuccessQueue() {
        return QueueBuilder.durable(QUEUE_PAYMENT_SUCCESS).build();
    }

    @Bean
    public Binding paymentSuccessBinding(Queue paymentSuccessQueue,
                                         TopicExchange learnhubExchange) {
        return BindingBuilder
                .bind(paymentSuccessQueue)
                .to(learnhubExchange)
                .with("payment.success");
    }
}
