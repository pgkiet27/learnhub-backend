package com.learnhub.user.config;

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

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    public static final String QUEUE_USER_REGISTERED = "user.service.user.registered";

    @Bean
    public Queue userRegisterQueue(){
        return QueueBuilder.durable(QUEUE_USER_REGISTERED)
                .build();
    }

    @Bean
    public Binding userRegisteredBinding(Queue userRegisteredQueue, TopicExchange learnhubExchange) {
        return BindingBuilder
                .bind(userRegisteredQueue)
                .to(learnhubExchange)
                .with("user.registered");
        // Routing key "user.registered" from Identity service
    }
}