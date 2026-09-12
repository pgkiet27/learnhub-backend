package com.learnhub.payment.service;

import com.learnhub.common.exception.BadRequestException;
import com.learnhub.common.exception.ConflictException;
import com.learnhub.common.exception.InternalServerException;
import com.learnhub.common.exception.ResourceNotFoundException;
import com.learnhub.payment.client.CourseServiceClient;
import com.learnhub.payment.dto.request.RefundRequest;
import com.learnhub.payment.dto.response.CheckoutResponse;
import com.learnhub.payment.dto.response.CourseInfoResponse;
import com.learnhub.payment.entity.Payment;
import com.learnhub.payment.entity.Refund;
import com.learnhub.payment.repository.PaymentRepository;
import com.learnhub.payment.repository.RefundRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Unit Tests")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RefundRepository refundRepository;
    @Mock
    private CourseServiceClient courseServiceClient;
    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private PaymentService paymentService;

    private UUID userId;
    private UUID courseId;
    private CourseInfoResponse paidCourse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        courseId = UUID.randomUUID();

        paidCourse = CourseInfoResponse.builder()
                .id(courseId).title("Advanced Java").thumbnailUrl("thumb.jpg")
                .price(BigDecimal.valueOf(49.99)).status("published")
                .instructorId(UUID.randomUUID()).totalLessons(20)
                .build();

        // @Value("${stripe.currency}") is not auto-injected by Mockito/@InjectMocks (there's no
        // Spring context in a Unit Test) — set it manually via ReflectionTestUtils, matching the
        // exact field name "currency"
        ReflectionTestUtils.setField(paymentService, "currency", "usd");
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

    @Nested
    @DisplayName("createCheckout()")
    class CreateCheckoutTests {

        @Test
        @DisplayName("Checkout a paid course — creates PaymentIntent + saves a pending payment")
        void createCheckout_PaidCourse_ShouldSucceed() {
            given(courseServiceClient.getCourse(courseId)).willReturn(Optional.of(paidCourse));
            given(paymentRepository.existsByUserIdAndCourseIdAndStatus(
                    userId, courseId, Payment.Status.succeeded)).willReturn(false);
            given(paymentRepository.save(any(Payment.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            PaymentIntent fakeIntent = new PaymentIntent();
            fakeIntent.setId("pi_123");
            fakeIntent.setClientSecret("pi_123_secret_abc");

            try (MockedStatic<PaymentIntent> mocked = mockStatic(PaymentIntent.class)) {
                mocked.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                        .thenReturn(fakeIntent);

                CheckoutResponse response = paymentService.createCheckout(userId, courseId);

                assertThat(response.getClientSecret()).isEqualTo("pi_123_secret_abc");
                assertThat(response.getAmount()).isEqualByComparingTo("49.99");
            }

            then(paymentRepository).should(times(1)).save(any(Payment.class));
        }

        @Test
        @DisplayName("Checkout a free course — throws BadRequestException")
        void createCheckout_FreeCourse_ShouldThrowBadRequest() {
            CourseInfoResponse freeCourse = CourseInfoResponse.builder()
                    .id(courseId).title("Free course").price(BigDecimal.ZERO)
                    .status("published").instructorId(UUID.randomUUID()).build();
            given(courseServiceClient.getCourse(courseId)).willReturn(Optional.of(freeCourse));

            assertThatThrownBy(() -> paymentService.createCheckout(userId, courseId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("free");

            then(paymentRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("Checkout a course that does not exist — throws ResourceNotFoundException")
        void createCheckout_CourseNotFound_ShouldThrowException() {
            given(courseServiceClient.getCourse(courseId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.createCheckout(userId, courseId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Checkout a course already purchased successfully — throws ConflictException")
        void createCheckout_AlreadyPurchased_ShouldThrowConflictException() {
            given(courseServiceClient.getCourse(courseId)).willReturn(Optional.of(paidCourse));
            given(paymentRepository.existsByUserIdAndCourseIdAndStatus(
                    userId, courseId, Payment.Status.succeeded)).willReturn(true);

            assertThatThrownBy(() -> paymentService.createCheckout(userId, courseId))
                    .isInstanceOf(ConflictException.class);

            then(paymentRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("Stripe throws while creating PaymentIntent — translated to InternalServerException")
        void createCheckout_StripeError_ShouldThrowInternalServerException() {
            given(courseServiceClient.getCourse(courseId)).willReturn(Optional.of(paidCourse));
            given(paymentRepository.existsByUserIdAndCourseIdAndStatus(
                    userId, courseId, Payment.Status.succeeded)).willReturn(false);

            try (MockedStatic<PaymentIntent> mocked = mockStatic(PaymentIntent.class)) {
                mocked.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                        .thenThrow(mock(StripeException.class));

                assertThatThrownBy(() -> paymentService.createCheckout(userId, courseId))
                        .isInstanceOf(InternalServerException.class);
            }
        }
    }

    @Nested
    @DisplayName("handlePaymentSucceeded()")
    class HandlePaymentSucceededTests {

        @Test
        @DisplayName("Correctly computes the 30/70 revenue split and publishes PaymentSuccessEvent")
        void handlePaymentSucceeded_ShouldCalculateRevenueSplitAndPublish() {
            Payment payment = Payment.builder()
                    .id(UUID.randomUUID()).userId(userId).courseId(courseId)
                    .amount(BigDecimal.valueOf(49.99)).currency("usd")
                    .stripePaymentIntentId("pi_123")
                    .status(Payment.Status.pending)
                    .build();

            given(paymentRepository.findByStripePaymentIntentId("pi_123"))
                    .willReturn(Optional.of(payment));
            given(paymentRepository.save(any(Payment.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            paymentService.handlePaymentSucceeded(mockSucceededEvent("pi_123"));

            assertThat(payment.getStatus()).isEqualTo(Payment.Status.succeeded);
            assertThat(payment.getPlatformFeeAmount()).isEqualByComparingTo("15.00");
            assertThat(payment.getInstructorEarningAmount()).isEqualByComparingTo("34.99");
            assertThat(payment.getPlatformFeeAmount().add(payment.getInstructorEarningAmount()))
                    .isEqualByComparingTo(payment.getAmount());

            then(rabbitTemplate).should(times(1))
                    .convertAndSend(anyString(), eq("payment.success"), any(Object.class));
        }

        @Test
        @DisplayName("Duplicate webhook (already succeeded before) — does not recompute or publish again")
        void handlePaymentSucceeded_AlreadySucceeded_ShouldSkip() {
            Payment payment = Payment.builder()
                    .id(UUID.randomUUID()).stripePaymentIntentId("pi_123")
                    .amount(BigDecimal.valueOf(49.99))
                    .status(Payment.Status.succeeded)
                    .build();

            given(paymentRepository.findByStripePaymentIntentId("pi_123"))
                    .willReturn(Optional.of(payment));

            paymentService.handlePaymentSucceeded(mockSucceededEvent("pi_123"));

            then(paymentRepository).should(never()).save(any());
            then(rabbitTemplate).should(never())
                    .convertAndSend(anyString(), anyString(), any(Object.class));
        }

        @Test
        @DisplayName("Webhook for a PaymentIntent that does not exist in the DB — throws IllegalStateException")
        void handlePaymentSucceeded_UnknownPaymentIntent_ShouldThrowException() {
            given(paymentRepository.findByStripePaymentIntentId("pi_unknown"))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    paymentService.handlePaymentSucceeded(mockSucceededEvent("pi_unknown")))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // Helper — simulates a Stripe Event of type payment_intent.succeeded

    @Nested
    @DisplayName("refundPayment()")
    class RefundPaymentTests {

        @Test
        @DisplayName("Full refund — payment moves to refunded")
        void refundPayment_FullAmount_ShouldMarkRefunded() {
            Payment payment = Payment.builder()
                    .id(UUID.randomUUID()).amount(BigDecimal.valueOf(49.99)).currency("usd")
                    .stripePaymentIntentId("pi_123").status(Payment.Status.succeeded)
                    .build();

            given(paymentRepository.findById(payment.getId())).willReturn(Optional.of(payment));
            given(refundRepository.findByPaymentId(payment.getId())).willReturn(List.of());
            given(refundRepository.save(any(Refund.class))).willAnswer(inv -> inv.getArgument(0));

            com.stripe.model.Refund fakeRefund = new com.stripe.model.Refund();
            fakeRefund.setId("re_123");
            fakeRefund.setStatus("succeeded");

            RefundRequest request = new RefundRequest();
            request.setReason("Student is not satisfied");

            try (MockedStatic<com.stripe.model.Refund> mocked =
                         mockStatic(com.stripe.model.Refund.class)) {
                mocked.when(() -> com.stripe.model.Refund.create(any(RefundCreateParams.class)))
                        .thenReturn(fakeRefund);

                paymentService.refundPayment(payment.getId(), request);
            }

            assertThat(payment.getStatus()).isEqualTo(Payment.Status.refunded);
            then(paymentRepository).should(times(1)).save(payment);
        }

        @Test
        @DisplayName("Refund exceeding the remaining amount — throws BadRequestException")
        void refundPayment_ExceedsRemaining_ShouldThrowBadRequest() {
            Payment payment = Payment.builder()
                    .id(UUID.randomUUID()).amount(BigDecimal.valueOf(49.99))
                    .status(Payment.Status.succeeded).build();

            Refund existingRefund = Refund.builder()
                    .amount(BigDecimal.valueOf(40.00)).status(Refund.Status.succeeded).build();

            given(paymentRepository.findById(payment.getId())).willReturn(Optional.of(payment));
            given(refundRepository.findByPaymentId(payment.getId()))
                    .willReturn(List.of(existingRefund));

            RefundRequest request = new RefundRequest();
            request.setAmount(BigDecimal.valueOf(20.00)); // only 9.99 remains
            request.setReason("Requesting a refund larger than the remaining amount");

            assertThatThrownBy(() -> paymentService.refundPayment(payment.getId(), request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("exceeds");
        }

        @Test
        @DisplayName("Refund a transaction that has not succeeded — throws BadRequestException")
        void refundPayment_NotSucceeded_ShouldThrowBadRequest() {
            Payment payment = Payment.builder()
                    .id(UUID.randomUUID()).status(Payment.Status.pending).build();

            given(paymentRepository.findById(payment.getId())).willReturn(Optional.of(payment));

            RefundRequest request = new RefundRequest();
            request.setReason("...");

            assertThatThrownBy(() -> paymentService.refundPayment(payment.getId(), request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("successfully paid");
        }
    }
}