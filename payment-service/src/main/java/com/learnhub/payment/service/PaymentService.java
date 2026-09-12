package com.learnhub.payment.service;

import com.learnhub.common.dto.PageResponse;
import com.learnhub.common.event.PaymentSuccessEvent;
import com.learnhub.common.exception.BadRequestException;
import com.learnhub.common.exception.ConflictException;
import com.learnhub.common.exception.InternalServerException;
import com.learnhub.common.exception.ResourceNotFoundException;
import com.learnhub.payment.client.CourseServiceClient;
import com.learnhub.payment.config.RabbitMQConfig;
import com.learnhub.payment.dto.request.RefundRequest;
import com.learnhub.payment.dto.response.CheckoutResponse;
import com.learnhub.payment.dto.response.CourseInfoResponse;
import com.learnhub.payment.dto.response.PaymentHistoryResponse;
import com.learnhub.payment.dto.response.RefundResponse;
import com.learnhub.payment.entity.Payment;
import com.learnhub.payment.entity.Refund;
import com.learnhub.payment.repository.PaymentRepository;
import com.learnhub.payment.repository.RefundRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.30");
    private static final int REFUND_SCALE = 2;

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final CourseServiceClient courseServiceClient;
    private final RabbitTemplate rabbitTemplate;

    @Value("${stripe.currency}")
    private String currency;

    @Transactional
    public CheckoutResponse createCheckout(UUID userId, UUID courseId) {
        CourseInfoResponse course = courseServiceClient.getCourse(courseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "COURSE_NOT_FOUND", "Course does not exist or is not published yet"));

        if (course.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("COURSE_IS_FREE",
                    "This course is free, please enroll directly via the Enrollment Service");
        }

        if (paymentRepository.existsByUserIdAndCourseIdAndStatus(
                userId, courseId, Payment.Status.succeeded)) {
            throw new ConflictException("ALREADY_PURCHASED", "You have already purchased this course");
        }

        PaymentIntent intent = createStripePaymentIntent(userId, courseId, course.getPrice());

        Payment payment = Payment.builder()
                .userId(userId)
                .courseId(courseId)
                .instructorId(course.getInstructorId())
                .courseTitle(course.getTitle())
                .courseThumbnailUrl(course.getThumbnailUrl())
                .amount(course.getPrice())
                .currency(currency)
                .stripePaymentIntentId(intent.getId())
                .build();

        Payment saved = paymentRepository.save(payment);
        log.info("Created payment {} (PaymentIntent {}) for user {} course {}",
                saved.getId(), intent.getId(), userId, courseId);

        return CheckoutResponse.builder()
                .paymentId(saved.getId())
                .clientSecret(intent.getClientSecret())
                .amount(saved.getAmount())
                .currency(saved.getCurrency())
                .build();
    }

    private PaymentIntent createStripePaymentIntent(UUID userId, UUID courseId, BigDecimal amount) {
        try {
            // Stripe charges in the smallest currency unit (cents for USD) — 49.99 usd -> 4999
            long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValueExact();

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(currency)
                    .putMetadata("userId", userId.toString())
                    .putMetadata("courseId", courseId.toString())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build())
                    .build();

            return PaymentIntent.create(params);

        } catch (StripeException e) {
            log.error("Stripe PaymentIntent creation failed for user {} course {}: {}",
                    userId, courseId, e.getMessage());
            throw new InternalServerException("STRIPE_ERROR",
                    "Unable to initiate payment, please try again later");
        }
    }

    @Transactional
    public void handlePaymentSucceeded(Event event) {
        PaymentIntent intent = extractPaymentIntent(event);

        Payment payment = paymentRepository.findByStripePaymentIntentId(intent.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Received webhook for a PaymentIntent that does not exist in the DB: " + intent.getId()));

        // Idempotency: Stripe delivers webhooks "at-least-once" — the same event can be
        // redelivered if the server responded slowly/failed last time. Skip reprocessing if
        // already succeeded, to avoid publishing PaymentSuccessEvent twice → duplicate enrollment.
        if (payment.getStatus() == Payment.Status.succeeded) {
            log.warn("Payment {} is already succeeded, skipping duplicate webhook", payment.getId());
            return;
        }

        BigDecimal platformFee = payment.getAmount()
                .multiply(PLATFORM_FEE_RATE)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal instructorEarning = payment.getAmount().subtract(platformFee);

        payment.setStatus(Payment.Status.succeeded);
        payment.setPlatformFeeAmount(platformFee);
        payment.setInstructorEarningAmount(instructorEarning);
        payment.setPaidAt(Instant.now());
        paymentRepository.save(payment);

        publishPaymentSuccessEvent(payment);

        log.info("Payment {} succeeded — platform fee {} / instructor earning {}",
                payment.getId(), platformFee, instructorEarning);
    }

    @Transactional
    public void handlePaymentFailed(Event event) {
        PaymentIntent intent = extractPaymentIntent(event);

        paymentRepository.findByStripePaymentIntentId(intent.getId())
                .ifPresentOrElse(
                        payment -> {
                            payment.setStatus(Payment.Status.failed);
                            paymentRepository.save(payment);
                            log.info("Payment {} moved to failed", payment.getId());
                        },
                        () -> log.warn("Received payment_intent.payment_failed webhook for a " +
                                "PaymentIntent that does not exist in the DB: {}", intent.getId())
                );
    }

    private PaymentIntent extractPaymentIntent(Event event) {
        return (PaymentIntent) event.getDataObjectDeserializer().getObject()
                .orElseThrow(() -> new IllegalStateException(
                        "Could not deserialize PaymentIntent from event " + event.getId()));
    }

    private void publishPaymentSuccessEvent(Payment payment) {
        PaymentSuccessEvent event = PaymentSuccessEvent.builder()
                .transactionId(payment.getId())
                .userId(payment.getUserId())
                .courseId(payment.getCourseId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .build();

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, "payment.success", event);
        log.info("Published PaymentSuccessEvent for payment {}", payment.getId());
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentHistoryResponse> getPaymentHistory(UUID userId, Pageable pageable) {
        return PageResponse.of(
                paymentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                        .map(this::toPaymentHistoryResponse));
    }

    private PaymentHistoryResponse toPaymentHistoryResponse(Payment payment) {
        List<RefundResponse> refunds = refundRepository.findByPaymentId(payment.getId()).stream()
                .map(this::toRefundResponse)
                .toList();

        return PaymentHistoryResponse.builder()
                .id(payment.getId())
                .courseId(payment.getCourseId())
                .courseTitle(payment.getCourseTitle())
                .courseThumbnailUrl(payment.getCourseThumbnailUrl())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus().name())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .refunds(refunds)
                .build();
    }

    private RefundResponse toRefundResponse(Refund refund) {
        return RefundResponse.builder()
                .id(refund.getId())
                .paymentId(refund.getPayment().getId())
                .amount(refund.getAmount())
                .reason(refund.getReason())
                .status(refund.getStatus().name())
                .refundedAt(refund.getRefundedAt())
                .createdAt(refund.getCreatedAt())
                .build();
    }

    @Transactional
    public RefundResponse refundPayment(UUID paymentId, RefundRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PAYMENT_NOT_FOUND", "Transaction does not exist"));

        if (payment.getStatus() != Payment.Status.succeeded
                && payment.getStatus() != Payment.Status.partially_refunded) {
            throw new BadRequestException("PAYMENT_NOT_REFUNDABLE",
                    "Only successfully paid transactions can be refunded. Current status: "
                            + payment.getStatus());
        }

        BigDecimal alreadyRefunded = refundRepository.findByPaymentId(paymentId).stream()
                .filter(r -> r.getStatus() == Refund.Status.succeeded)
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remaining = payment.getAmount().subtract(alreadyRefunded);
        BigDecimal refundAmount = request.getAmount() != null ? request.getAmount() : remaining;

        if (refundAmount.compareTo(remaining) > 0) {
            throw new BadRequestException("REFUND_AMOUNT_EXCEEDS_REMAINING",
                    "Refund amount (" + refundAmount + ") exceeds the remaining refundable amount (" + remaining + ")");
        }

        com.stripe.model.Refund stripeRefund = createStripeRefund(
                payment.getStripePaymentIntentId(), refundAmount);

        Refund refund = Refund.builder()
                .payment(payment)
                .stripeRefundId(stripeRefund.getId())
                .amount(refundAmount)
                .reason(request.getReason())
                .status(mapStripeRefundStatus(stripeRefund.getStatus()))
                .build();

        if (refund.getStatus() == Refund.Status.succeeded) {
            refund.setRefundedAt(Instant.now());

            BigDecimal totalRefunded = alreadyRefunded.add(refundAmount);
            payment.setStatus(totalRefunded.compareTo(payment.getAmount()) >= 0
                    ? Payment.Status.refunded
                    : Payment.Status.partially_refunded);
            paymentRepository.save(payment);
        }

        Refund saved = refundRepository.save(refund);
        log.info("Refunded {} {} for payment {} (Stripe refund {}, status {})",
                refundAmount, payment.getCurrency(), paymentId, stripeRefund.getId(), refund.getStatus());

        return toRefundResponse(saved);
    }

    private com.stripe.model.Refund createStripeRefund(String paymentIntentId, BigDecimal amount) {
        try {
            long amountInCents = amount
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();

            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .setAmount(amountInCents)
                    .build();

            return com.stripe.model.Refund.create(params);

        } catch (StripeException e) {
            log.error("Stripe refund creation failed for PaymentIntent {}: {}",
                    paymentIntentId, e.getMessage());
            throw new InternalServerException("STRIPE_REFUND_ERROR",
                    "Unable to process the refund, please try again later");
        }
    }

    private Refund.Status mapStripeRefundStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "succeeded" -> Refund.Status.succeeded;
            case "failed" -> Refund.Status.failed;
            default -> Refund.Status.pending; // "pending", "requires_action"
        };
    }
}