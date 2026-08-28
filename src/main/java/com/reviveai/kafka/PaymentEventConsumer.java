package com.reviveai.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    @KafkaListener(
            topics = "payment.events.test",
            groupId = "reviveai-recovery-group"
    )
    public void consumePaymentEvent(String message) {

        String eventId = null;

        try {

            log.info("Payment event received from Kafka");

            // ------------------------------------------------
            // 1. Validate Kafka message
            // ------------------------------------------------

            if (message == null || message.isBlank()) {

                log.warn(
                        "Ignoring empty Kafka payment event"
                );

                return;
            }

            // ------------------------------------------------
            // 2. Deserialize Kafka message
            // ------------------------------------------------

            PaymentFailedEvent event =
                    objectMapper.readValue(
                            message,
                            PaymentFailedEvent.class
                    );

            // ------------------------------------------------
            // 3. Validate event structure
            // ------------------------------------------------

            if (event.getPayload() == null ||
                    event.getPayload().getPayment() == null ||
                    event.getPayload().getPayment().getEntity() == null) {

                log.warn(
                        "Invalid payment event: payment entity is missing"
                );

                return;
            }

            PaymentFailedEvent.Entity payment =
                    event.getPayload()
                            .getPayment()
                            .getEntity();

            // ------------------------------------------------
            // 4. Validate payment ID
            // ------------------------------------------------

            if (payment.getId() == null ||
                    payment.getId().isBlank()) {

                log.warn(
                        "Invalid payment event: payment ID is missing"
                );

                return;
            }

            /*
             * The current event model does not contain a
             * dedicated Razorpay event ID.
             *
             * Therefore, payment ID is currently used as
             * the idempotency identifier.
             */
            eventId = payment.getId();

            // ------------------------------------------------
            // 5. Redis idempotency check
            // ------------------------------------------------

            boolean firstProcessing =
                    idempotencyService.tryMarkAsProcessed(
                            eventId
                    );

            if (!firstProcessing) {

                log.warn(
                        "Duplicate Kafka payment event ignored. " +
                                "eventId={}, paymentId={}",
                        eventId,
                        payment.getId()
                );

                return;
            }

            // ------------------------------------------------
            // 6. Resolve customer ID
            // ------------------------------------------------

            String customerId = payment.getCustomerId();

            /*
             * Some payment events contain customer_id inside
             * payment.entity.
             *
             * Our test event contains customer.entity.id at
             * the top level instead.
             *
             * Use the payment customer_id first, then fall
             * back to the top-level customer entity.
             */
            if (event.getPayload().getCustomer() != null &&
                    event.getPayload().getCustomer().getEntity() != null) {

                customerId =
                        event.getPayload()
                                .getCustomer()
                                .getEntity()
                                .getId();
            }

            // ------------------------------------------------
            // 7. Log payment failure
            // ------------------------------------------------

            log.info(
                    "Payment failure received. " +
                            "PaymentId: {}, CustomerId: {}, " +
                            "Amount: {}, Currency: {}, ErrorCode: {}",
                    payment.getId(),
                    customerId,
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getErrorCode()
            );

            // ------------------------------------------------
            // 8. Process payment recovery
            // ------------------------------------------------

            paymentRecoveryService.processPaymentFailure(event);

            log.info(
                    "Payment recovery processing completed. " +
                            "paymentId={}, eventId={}",
                    payment.getId(),
                    eventId
            );

        } catch (Exception exception) {

            /*
             * If processing fails after the Redis idempotency
             * key was created, remove the key so Kafka can
             * retry the event.
             */
            if (eventId != null) {

                idempotencyService.removeProcessedMark(
                        eventId
                );
            }

            log.error(
                    "Failed to process payment event from Kafka. " +
                            "eventId={}",
                    eventId,
                    exception
            );

            throw new RuntimeException(
                    "Failed to process payment event",
                    exception
            );
        }
    }
}

