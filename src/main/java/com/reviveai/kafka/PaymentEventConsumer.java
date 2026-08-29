package com.reviveai.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviveai.config.KafkaConfig;
import com.reviveai.dto.PaymentFailedEvent;
import com.reviveai.service.IdempotencyService;
import com.reviveai.service.PaymentRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final ObjectMapper objectMapper;
    private final PaymentRecoveryService paymentRecoveryService;
    private final IdempotencyService idempotencyService;

    // ============================================================
    // KAFKA LISTENER
    // ============================================================

    @KafkaListener(
            topics = KafkaConfig.RAW_PAYMENT_EVENTS_TOPIC,
            groupId = "reviveai-recovery-group"
    )
    public void consumePaymentEvent(String message) {

        String eventId = null;

        try {

            log.info(
                    "Payment event received from Kafka. topic={}",
                    KafkaConfig.RAW_PAYMENT_EVENTS_TOPIC
            );

            // ====================================================
            // 1. VALIDATE KAFKA MESSAGE
            // ====================================================

            if (message == null || message.isBlank()) {

                log.warn(
                        "Ignoring empty Kafka payment event"
                );

                return;
            }

            // ====================================================
            // 2. DESERIALIZE KAFKA MESSAGE
            // ====================================================

            PaymentFailedEvent event;

            try {

                event =
                        objectMapper.readValue(
                                message,
                                PaymentFailedEvent.class
                        );

            } catch (Exception exception) {

                log.error(
                        "Failed to deserialize Kafka payment event",
                        exception
                );

                /*
                 * Invalid JSON cannot be processed successfully.
                 *
                 * Throwing the exception allows the Kafka
                 * error-handling mechanism to deal with it.
                 */

                throw new IllegalArgumentException(
                        "Invalid payment event JSON",
                        exception
                );
            }

            // ====================================================
            // 3. VALIDATE EVENT STRUCTURE
            // ====================================================

            if (event == null) {

                log.warn(
                        "Ignoring null deserialized payment event"
                );

                return;
            }

            if (event.getPayload() == null) {

                log.warn(
                        "Invalid payment event: payload is missing"
                );

                return;
            }

            if (event.getPayload().getPayment() == null) {

                log.warn(
                        "Invalid payment event: payment object is missing"
                );

                return;
            }

            if (event.getPayload()
                    .getPayment()
                    .getEntity() == null) {

                log.warn(
                        "Invalid payment event: payment entity is missing"
                );

                return;
            }

            // ====================================================
            // 4. EXTRACT PAYMENT
            // ====================================================

            PaymentFailedEvent.Entity payment =
                    event.getPayload()
                            .getPayment()
                            .getEntity();

            // ====================================================
            // 5. VALIDATE PAYMENT ID
            // ====================================================

            if (payment.getId() == null
                    || payment.getId().isBlank()) {

                log.warn(
                        "Invalid payment event: payment ID is missing"
                );

                return;
            }

            /*
             * The current PaymentFailedEvent model does not contain
             * a dedicated Razorpay webhook event ID.
             *
             * Therefore payment ID is used as the downstream
             * idempotency identifier.
             */

            eventId = payment.getId();

            // ====================================================
            // 6. REDIS IDEMPOTENCY CHECK
            // ====================================================

            boolean firstProcessing =
                    idempotencyService.tryMarkAsProcessed(
                            eventId
                    );

            if (!firstProcessing) {

                log.info(
                        "Duplicate Kafka payment event ignored. " +
                                "eventId={}, paymentId={}",
                        eventId,
                        payment.getId()
                );

                return;
            }

            // ====================================================
            // 7. RESOLVE CUSTOMER ID
            // ====================================================

            String customerId =
                    payment.getCustomerId();

            /*
             * Prefer payment.entity.customer_id.
             *
             * If it is not available, fall back to:
             *
             * payload.customer.entity.id
             */

            if ((customerId == null
                    || customerId.isBlank())
                    && event.getPayload().getCustomer() != null
                    && event.getPayload()
                    .getCustomer()
                    .getEntity() != null) {

                customerId =
                        event.getPayload()
                                .getCustomer()
                                .getEntity()
                                .getId();
            }

            // ====================================================
            // 8. LOG PAYMENT FAILURE
            // ====================================================

            log.info(
                    "Payment failure received. " +
                            "paymentId={}, customerId={}, " +
                            "amount={}, currency={}, " +
                            "errorCode={}, errorDescription={}",
                    payment.getId(),
                    customerId,
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getErrorCode(),
                    payment.getErrorDescription()
            );

            // ====================================================
            // 9. PROCESS PAYMENT RECOVERY
            // ====================================================

            paymentRecoveryService.processPaymentFailure(
                    event
            );

            // ====================================================
            // 10. SUCCESS
            // ====================================================

            log.info(
                    "Payment recovery processing completed successfully. " +
                            "paymentId={}, eventId={}",
                    payment.getId(),
                    eventId
            );

        } catch (Exception exception) {

            // ====================================================
            // 11. REMOVE IDEMPOTENCY MARK ON FAILURE
            // ====================================================

            /*
             * If processing failed after Redis marked this event
             * as processed, remove the mark.
             *
             * This allows Kafka to retry the message.
             */

            if (eventId != null) {

                try {

                    idempotencyService.removeProcessedMark(
                            eventId
                    );

                    log.info(
                            "Removed Redis idempotency mark after " +
                                    "payment event failure. eventId={}",
                            eventId
                    );

                } catch (Exception idempotencyException) {

                    /*
                     * Do not hide the original processing failure
                     * if Redis cleanup itself fails.
                     */

                    log.error(
                            "Failed to remove Redis idempotency mark. " +
                                    "eventId={}",
                            eventId,
                            idempotencyException
                    );
                }
            }

            // ====================================================
            // 12. LOG FAILURE
            // ====================================================

            log.error(
                    "Failed to process payment event from Kafka. " +
                            "eventId={}",
                    eventId,
                    exception
            );

            // ====================================================
            // 13. PROPAGATE FAILURE TO KAFKA
            // ====================================================

            throw new RuntimeException(
                    "Failed to process payment event",
                    exception
            );
        }
    }
}

