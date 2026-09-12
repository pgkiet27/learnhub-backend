package com.learnhub.payment.repository;

import com.learnhub.payment.AbstractIntegrationTest;
import com.learnhub.payment.entity.Payment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PaymentRepository Integration Tests")
@Transactional
class PaymentRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("Save and existsByUserIdAndCourseIdAndStatus — succeeds")
    void saveAndCheckExists_ShouldWork() {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        Payment payment = Payment.builder()
                .userId(userId).courseId(courseId).instructorId(UUID.randomUUID())
                .courseTitle("Test Course").amount(BigDecimal.valueOf(49.99)).currency("usd")
                .stripePaymentIntentId("pi_" + UUID.randomUUID())
                .status(Payment.Status.succeeded)
                .build();
        paymentRepository.save(payment);

        boolean exists = paymentRepository.existsByUserIdAndCourseIdAndStatus(
                userId, courseId, Payment.Status.succeeded);

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("UNIQUE(stripe_payment_intent_id) — a duplicate PaymentIntent is rejected by the DB")
    void save_DuplicateStripePaymentIntentId_ShouldViolateUniqueConstraint() {
        String intentId = "pi_" + UUID.randomUUID();

        paymentRepository.saveAndFlush(Payment.builder()
                .userId(UUID.randomUUID()).courseId(UUID.randomUUID())
                .instructorId(UUID.randomUUID()).courseTitle("Course A")
                .amount(BigDecimal.TEN).currency("usd")
                .stripePaymentIntentId(intentId)
                .build());

        Payment duplicate = Payment.builder()
                .userId(UUID.randomUUID()).courseId(UUID.randomUUID())
                .instructorId(UUID.randomUUID()).courseTitle("Course B")
                .amount(BigDecimal.TEN).currency("usd")
                .stripePaymentIntentId(intentId)   // Same PaymentIntent ID — cannot really happen
                .build();                          // (Stripe always generates a unique ID), but this tests the DB constraint

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}