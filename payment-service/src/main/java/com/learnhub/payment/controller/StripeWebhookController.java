package com.learnhub.payment.controller;

import com.learnhub.payment.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Payments", description = "Internal webhook receiving events from Stripe")
@RestController
@RequestMapping("/api/v1/payments/webhook")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final PaymentService paymentService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Operation(summary = "Receive events from Stripe (payment_intent.succeeded/failed)")
    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        switch (event.getType()) {
            case "payment_intent.succeeded" -> paymentService.handlePaymentSucceeded(event);
            case "payment_intent.payment_failed" -> paymentService.handlePaymentFailed(event);
            default -> log.debug("Ignoring unhandled Stripe event type: {}", event.getType());
        }

        // Always return 200 once the signature is verified, even for event types we don't
        // handle — returning anything other than 200 makes Stripe treat it as an error and
        // RETRY this event repeatedly
        return ResponseEntity.ok("OK");
    }
}