package com.learnhub.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String EXCHANGE_NAME = "learnhub.events";
    public static final String QUEUE_CHURN_HIGH_RISK = "notification.service.churn.high_risk";
    public static final String ROUTING_KEY_REMINDER_DELIVERED = "churn.reminder_delivered";

    // Notification Service consumes:
    // - churn.high_risk → learning reminder email (published by Enrollment Service's churn job)
    // Notification Service publishes:
    // - churn.reminder_delivered → the reminder email was sent (consumed by Enrollment Service)

    @Bean
    public TopicExchange learnhubExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_NAME).durable(true).build();
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public Queue churnHighRiskQueue() {
        return QueueBuilder.durable(QUEUE_CHURN_HIGH_RISK).build();
    }

    @Bean
    public Binding churnHighRiskBinding(Queue churnHighRiskQueue, TopicExchange learnhubExchange) {
        return BindingBuilder.bind(churnHighRiskQueue).to(learnhubExchange).with("churn.high_risk");
    }
}
