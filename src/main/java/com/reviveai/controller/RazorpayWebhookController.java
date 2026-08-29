package com.reviveai.controller;

import com.fasterxml.jackson.databind.JsonNode;
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

    // =========================================================
    // RAZORPAY WEBHOOK
    // =========================================================

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

        log.info(
                "Razorpay webhook received. eventId={}",
                eventId
        );

        // =====================================================
        // 1. VALIDATE RAW PAYLOAD
        // =====================================================

        if (rawPayload == null || rawPayload.isBlank()) {

            log.warn(
                    "Razorpay webhook rejected because payload is empty"
            );

            return ResponseEntity
                    .badRequest()
                    .body("Empty webhook payload");
        }

        // =====================================================
        // 2. VALIDATE EVENT ID
        // =====================================================

        if (eventId == null || eventId.isBlank()) {

            log.warn(
                    "Razorpay webhook rejected because event ID is missing"
            );

            return ResponseEntity
                    .badRequest()
                    .body("Missing event ID");
        }

        // =====================================================
        // 3. VALIDATE SIGNATURE
        // =====================================================

        boolean validSignature =
                webhookSafetyService.isValidSignature(
                        rawPayload,
                        signature,
                        webhookSecret
                );

        if (!validSignature) {

            log.warn(
                    "Invalid Razorpay webhook signature. eventId={}",
                    eventId
            );

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid signature");
        }

        // =====================================================
        // 4. VALIDATE JSON
        // =====================================================

        try {

            JsonNode root =
                    objectMapper.readTree(rawPayload);

            if (root == null || root.isNull()) {

                log.warn(
                        "Razorpay webhook contains empty JSON. eventId={}",
                        eventId
                );

                return ResponseEntity
                        .badRequest()
                        .body("Invalid JSON payload");
            }

        } catch (Exception exception) {

            log.warn(
                    "Invalid JSON received from Razorpay. eventId={}",
                    eventId,
                    exception
            );

            return ResponseEntity
                    .badRequest()
                    .body("Invalid JSON");
        }

        // =====================================================
        // 5. PUBLISH RAW EVENT TO KAFKA
        // =====================================================

        try {

            kafkaTemplate
                    .send(
                            KafkaConfig.RAW_PAYMENT_EVENTS_TOPIC,
                            eventId,
                            rawPayload
                    )
                    .get(
                            10,
                            TimeUnit.SECONDS
                    );

            log.info(
                    "Razorpay webhook published to Kafka successfully. " +
                            "eventId={}, topic={}",
                    eventId,
                    KafkaConfig.RAW_PAYMENT_EVENTS_TOPIC
            );

            // =================================================
            // 6. ACKNOWLEDGE RAZORPAY
            // =================================================

            return ResponseEntity
                    .ok()
                    .body("Webhook accepted");

        } catch (Exception exception) {

            // =================================================
            // 7. LOG KAFKA FAILURE
            // =================================================

            log.error(
                    "Failed to publish Razorpay webhook to Kafka. " +
                            "eventId={}, topic={}",
                    eventId,
                    KafkaConfig.RAW_PAYMENT_EVENTS_TOPIC,
                    exception
            );

            // =================================================
            // 8. RETURN NON-2XX
            // =================================================

            /*
             * Kafka did not successfully accept the webhook.
             *
             * Therefore we do NOT acknowledge the webhook as
             * successfully processed.
             *
             * Returning 500 allows the payment provider to retry.
             */

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to process webhook");
        }
    }
}

