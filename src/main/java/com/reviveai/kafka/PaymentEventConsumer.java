package com.reviveai.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviveai.config.KafkaConfig;
import com.reviveai.dto.PaymentFailedEvent;
import com.reviveai.service.IdempotencyService;
import com.reviveai.service.PaymentRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
    public void consumePaymentEvent(
            ConsumerRecord<String, String> record
    ) {

        String eventId = null;

        try {

            // ====================================================
            // 1. VALIDATE KAFKA RECORD
            // ====================================================

            if (record == null) {

                log.warn(
                        "Ignoring null Kafka record"
                );

                return;
            }

            // ====================================================
            // 2. EXTRACT KAFKA KEY AND VALUE
            // ====================================================

            eventId = record.key();

            String message = record.value();

            log.info(
                    "Payment event received from Kafka. " +
                            "topic={}, partition={}, offset={}, eventId={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    eventId
            );

            // ====================================================
            // 3. VALIDATE EVENT ID
            // ====================================================

            if (eventId == null || eventId.isBlank()) {

                log.warn(
                        "Ignoring Kafka payment event because " +
                                "Kafka event ID/key is missing. " +
                                "topic={}, partition={}, offset={}",
                        record.topic(),
                        record.partition(),
                        record.offset()
                );

                return;
            }

            // ====================================================
            // 4. VALIDATE MESSAGE
            // ====================================================

            if (message == null || message.isBlank()) {

                log.warn(
                        "Ignoring empty Kafka payment event. " +
                                "eventId={}",
                        eventId
                );

                return;
            }

            // ====================================================
            // 5. DESERIALIZE EVENT
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
                        "Failed to deserialize Kafka payment event. " +
                                "eventId={}",
                        eventId,
                        exception
                );

                throw new IllegalArgumentException(
                        "Invalid payment event JSON",
                        exception
                );
            }

            // ====================================================
            // 6. VALIDATE EVENT
            // ====================================================

            if (event == null) {

                log.warn(
                        "Ignoring null payment event. " +
                                "eventId={}",
                        eventId
                );

                return;
            }

            // ====================================================
            // 7. VALIDATE EVENT TYPE
            // ====================================================

            String eventType =
                    event.getEvent();

            if (eventType == null
                    || eventType.isBlank()) {

                log.warn(
                        "Invalid payment event: event type is missing. " +
                                "eventId={}",
                        eventId
                );

                return;
            }

            log.info(
                    "Payment webhook event type resolved. " +
                            "eventId={}, event={}",
                    eventId,
                    eventType
            );

            // ====================================================
            // 8. ACCEPT SUPPORTED EVENTS
            // ====================================================

            boolean paymentFailed =
                    "payment.failed".equalsIgnoreCase(eventType);

            boolean paymentCaptured =
                    "payment.captured".equalsIgnoreCase(eventType);

            if (!paymentFailed && !paymentCaptured) {

                log.info(
                        "Ignoring unsupported payment event. " +
                                "eventId={}, event={}",
                        eventId,
                        eventType
                );

                return;
            }

            // ====================================================
            // 9. VALIDATE PAYLOAD
            // ====================================================

            if (event.getPayload() == null) {

                log.warn(
                        "Invalid payment event: payload is missing. " +
                                "eventId={}, event={}",
                        eventId,
                        eventType
                );

                return;
            }

            // ====================================================
            // 10. VALIDATE PAYMENT
            // ====================================================

            if (event.getPayload().getPayment() == null) {

                log.warn(
                        "Invalid payment event: payment object missing. " +
                                "eventId={}, event={}",
                        eventId,
                        eventType
                );

                return;
            }

            // ====================================================
            // 11. VALIDATE PAYMENT ENTITY
            // ====================================================

            if (event.getPayload()
                    .getPayment()
                    .getEntity() == null) {

                log.warn(
                        "Invalid payment event: payment entity missing. " +
                                "eventId={}, event={}",
                        eventId,
                        eventType
                );

                return;
            }

            // ====================================================
            // 12. EXTRACT PAYMENT
            // ====================================================

            PaymentFailedEvent.Entity payment =
                    event.getPayload()
                            .getPayment()
                            .getEntity();

            // ====================================================
            // 13. VALIDATE PAYMENT ID
            // ====================================================

            if (payment.getId() == null
                    || payment.getId().isBlank()) {

                log.warn(
                        "Invalid payment event: payment ID missing. " +
                                "eventId={}, event={}",
                        eventId,
                        eventType
                );

                return;
            }

            String paymentId =
                    payment.getId();

            // ====================================================
            // 14. REDIS IDEMPOTENCY
            // ====================================================

            boolean firstProcessing =
                    idempotencyService.tryMarkAsProcessed(
                            eventId
                    );

            if (!firstProcessing) {

                log.info(
                        "Duplicate payment event ignored. " +
                                "eventId={}, paymentId={}, event={}",
                        eventId,
                        paymentId,
                        eventType
                );

                return;
            }

            // ====================================================
            // 15. RESOLVE CUSTOMER ID
            // ====================================================

            String customerId =
                    payment.getCustomerId();

            /*
             * Preferred:
             *
             * payment.entity.customer_id
             *
             * Fallback:
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
            // 16. LOG EVENT
            // ====================================================

            log.info(
                    "Supported payment event received. " +
                            "eventId={}, event={}, paymentId={}, " +
                            "customerId={}, amount={}, currency={}, status={}, " +
                            "errorCode={}, errorDescription={}",
                    eventId,
                    eventType,
                    paymentId,
                    customerId,
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getStatus(),
                    payment.getErrorCode(),
                    payment.getErrorDescription()
            );

            // ====================================================
            // 17. ROUTE EVENT
            // ====================================================

            if (paymentFailed) {

                log.info(
                        "Routing payment.failed event to recovery pipeline. " +
                                "eventId={}, paymentId={}",
                        eventId,
                        paymentId
                );

                paymentRecoveryService.processPaymentFailure(
                        event
                );

            } else {

                log.info(
                        "Routing payment.captured event to recovery pipeline. " +
                                "eventId={}, paymentId={}",
                        eventId,
                        paymentId
                );

                paymentRecoveryService.processPaymentSuccess(
                        event
                );
            }

            // ====================================================
            // 18. SUCCESS
            // ====================================================

            log.info(
                    "Payment event processing completed successfully. " +
                            "eventId={}, event={}, paymentId={}",
                    eventId,
                    eventType,
                    paymentId
            );

        } catch (Exception exception) {

            // ====================================================
            // 19. REMOVE REDIS IDEMPOTENCY MARK
            // ====================================================

            if (eventId != null
                    && !eventId.isBlank()) {

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
            // 20. LOG FAILURE
            // ====================================================

            log.error(
                    "Failed to process payment event from Kafka. " +
                            "eventId={}",
                    eventId,
                    exception
            );

            // ====================================================
            // 21. PROPAGATE FAILURE TO KAFKA
            // ====================================================

            throw new RuntimeException(
                    "Failed to process payment event",
                    exception
            );
        }
    }
}