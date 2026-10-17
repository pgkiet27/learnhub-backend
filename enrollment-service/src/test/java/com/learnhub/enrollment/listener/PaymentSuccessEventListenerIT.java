package com.learnhub.enrollment.listener;

import com.learnhub.common.event.PaymentSuccessEvent;
import com.learnhub.enrollment.AbstractIntegrationTest;
import com.learnhub.enrollment.client.CourseServiceClient;
import com.learnhub.enrollment.config.RabbitMQConfig;
import com.learnhub.enrollment.dto.response.CourseInfoResponse;
import com.learnhub.enrollment.repository.EnrollmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.given;

@DisplayName("PaymentSuccessEventListener Integration Tests")
class PaymentSuccessEventListenerIT extends AbstractIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private EnrollmentRepository enrollmentRepository;

    // CourseServiceClient makes a real HTTP call to Course Service — no Course Service is running
    // in this test, so this part alone is mocked, while RabbitMQ/Postgres still use real containers
    @MockitoBean
    private CourseServiceClient courseServiceClient;

    @Test
    @DisplayName("Publish payment.success → automatically creates an enrollment (asynchronous)")
    void handlePaymentSuccess_ShouldAutoCreateEnrollment() {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        given(courseServiceClient.getCourse(courseId)).willReturn(Optional.of(
                CourseInfoResponse.builder()
                        .id(courseId).title("Paid Course").price(BigDecimal.valueOf(499000))
                        .status("published").totalLessons(8).build()));

        PaymentSuccessEvent event = PaymentSuccessEvent.builder()
                .transactionId(UUID.randomUUID())
                .userId(userId).courseId(courseId)
                .amount(BigDecimal.valueOf(499000)).currency("VND")
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME, "payment.success", event);

        // The listener runs on Rabbit's own thread — use Awaitility instead of a fixed
        // Thread.sleep, so the test isn't flaky when the CI machine is slower than the local one
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(enrollmentRepository.existsByUserIdAndCourseId(userId, courseId)).isTrue());

        var enrollment = enrollmentRepository.findByUserIdAndCourseId(userId, courseId).orElseThrow();
        assertThat(enrollment.getCourseTitle()).isEqualTo("Paid Course");
        assertThat(enrollment.getTotalLessons()).isEqualTo(8);
    }

    @Test
    @DisplayName("Publish duplicate payment.success (redelivered message) — does not create a 2nd enrollment")
    void handlePaymentSuccess_DuplicateEvent_ShouldNotCreateDuplicateEnrollment() {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        given(courseServiceClient.getCourse(courseId)).willReturn(Optional.of(
                CourseInfoResponse.builder()
                        .id(courseId).title("Paid Course").price(BigDecimal.valueOf(499000))
                        .status("published").totalLessons(8).build()));

        PaymentSuccessEvent event = PaymentSuccessEvent.builder()
                .transactionId(UUID.randomUUID())
                .userId(userId).courseId(courseId)
                .amount(BigDecimal.valueOf(499000)).currency("VND")
                .build();

        // Publish twice in a row — simulates RabbitMQ redelivering the message (at-least-once delivery)
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, "payment.success", event);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, "payment.success", event);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(enrollmentRepository.existsByUserIdAndCourseId(userId, courseId)).isTrue());

        // Wait a bit longer to make sure the 2nd message (if it were to cause an error) has had
        // time to be processed, then assert there is still exactly 1 enrollment — no duplicate row
        await().pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(6)).untilAsserted(() ->
                assertThat(enrollmentRepository.findByUserIdAndCourseId(userId, courseId)).isPresent());
    }
}