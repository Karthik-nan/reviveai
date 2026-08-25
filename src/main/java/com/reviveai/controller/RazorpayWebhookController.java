package com.reviveai.controller;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviveai.config.KafkaConfig;
import com.reviveai.service.WebhookSafetyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class RazorpayWebhookController {

    private final WebhookSafetyService webhookSafetyService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${razorpay.webhook.secret}")
    private String webhookSecret;

    @PostMapping("/razorpay")
    public ResponseEntity<String> handleRazorpayWebhook(

            @RequestHeader(
                    value = "X-Razorpay-Signature",
                    required = false
            )
            String signature,

            @RequestHeader(
                    value = "X-Razorpay-Event-Id",
                    required = false
            )
            String eventId,

            @RequestBody String rawPayload
    ) {

        log.info("Received Razorpay webhook");

        // ----------------------------------------------------
        // 1. Validate signature
        // ----------------------------------------------------
        boolean validSignature =
                webhookSafetyService.isValidSignature(
                        rawPayload,
                        signature,
                        webhookSecret
                );

        if (!validSignature) {

            log.warn("Invalid Razorpay webhook signature");

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid signature");
        }

        // ----------------------------------------------------
        // 2. Validate event ID
        // ----------------------------------------------------
        if (eventId == null || eventId.isBlank()) {

            log.warn("Razorpay webhook missing event ID");

            return ResponseEntity
                    .badRequest()
                    .body("Missing event ID");
        }

        // ----------------------------------------------------
        // 3. Validate JSON
        // ----------------------------------------------------
        try {

            objectMapper.readTree(rawPayload);

        } catch (Exception e) {

            log.warn("Invalid JSON received from Razorpay");

            return ResponseEntity
                    .badRequest()
                    .body("Invalid JSON");
        }

        // ----------------------------------------------------
        // 4. Acquire idempotency lock BEFORE Kafka
        // ----------------------------------------------------
        boolean firstDelivery =
                webhookSafetyService.acquireIdempotencyLock(eventId);

        if (!firstDelivery) {

            log.info(
                    "Duplicate Razorpay webhook ignored. eventId={}",
                    eventId
            );

            return ResponseEntity
                    .ok()
                    .body("Event already processed");
        }

        // ----------------------------------------------------
        // 5. Publish to Kafka
        // ----------------------------------------------------
        try {

            kafkaTemplate
                    .send(
                            KafkaConfig.RAW_PAYMENT_EVENTS_TOPIC,
                            eventId,
                            rawPayload
                    )
                    .get(10, TimeUnit.SECONDS);

            log.info(
                    "Razorpay webhook successfully published to Kafka. eventId={}",
                    eventId
            );

            return ResponseEntity
                    .ok()
                    .body("Webhook accepted");

        } catch (Exception e) {

            log.error(
                    "Failed to publish Razorpay webhook to Kafka. eventId={}",
                    eventId,
                    e
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to process webhook");
        }
    }
}
