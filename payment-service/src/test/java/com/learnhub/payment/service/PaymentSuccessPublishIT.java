package com.learnhub.payment.service;

import com.learnhub.common.event.PaymentSuccessEvent;
import com.learnhub.payment.AbstractIntegrationTest;
import com.learnhub.payment.config.RabbitMQConfig;
import com.learnhub.payment.entity.Payment;
import com.learnhub.payment.repository.PaymentRepository;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("PaymentService — Publish PaymentSuccessEvent Integration Tests")
class PaymentSuccessPublishIT extends AbstractIntegrationTest {

    private static final String TEST_QUEUE = "test.payment.success.listener";

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private RabbitAdmin rabbitAdmin;

    @BeforeEach
    void bindTestQueue() {
        Queue queue = QueueBuilder.durable(TEST_QUEUE).autoDelete().build();
        Binding binding = BindingBuilder.bind(queue)
                .to(new TopicExchange(RabbitMQConfig.EXCHANGE_NAME))
                .with("payment.success");

        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareBinding(binding);
        rabbitAdmin.purgeQueue(TEST_QUEUE, false);
    }

    @Test
    @DisplayName("handlePaymentSucceeded() publishes the correct PaymentSuccessEvent to real RabbitMQ")
    void handlePaymentSucceeded_ShouldPublishRealMessage() {
        Payment payment = paymentRepository.save(Payment.builder()
                .userId(UUID.randomUUID()).courseId(UUID.randomUUID())
                .instructorId(UUID.randomUUID()).courseTitle("Test Course")
                .amount(BigDecimal.valueOf(49.99)).currency("usd")
                .stripePaymentIntentId("pi_" + UUID.randomUUID())
                .build());

        paymentService.handlePaymentSucceeded(mockSucceededEvent(payment.getStripePaymentIntentId()));

        // The service's own RabbitTemplate (already configured with Jackson2JsonMessageConverter
        // in RabbitMQConfig, B1) automatically deserializes it back into the correct PaymentSuccessEvent type
        PaymentSuccessEvent received =
                (PaymentSuccessEvent) rabbitTemplate.receiveAndConvert(TEST_QUEUE, 5000);

        assertThat(received).isNotNull();
        assertThat(received.getTransactionId()).isEqualTo(payment.getId());
        assertThat(received.getUserId()).isEqualTo(payment.getUserId());
        assertThat(received.getCourseId()).isEqualTo(payment.getCourseId());
        assertThat(received.getAmount()).isEqualByComparingTo("49.99");
    }

    private Event mockSucceededEvent(String paymentIntentId) {
        PaymentIntent intent = new PaymentIntent();
        intent.setId(paymentIntentId);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        given(deserializer.getObject()).willReturn(Optional.of(intent));

        Event event = mock(Event.class);
        given(event.getDataObjectDeserializer()).willReturn(deserializer);
        return event;
    }
}