package com.reviveai.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviveai.config.KafkaConfig;
import com.reviveai.dto.PaymentFailedEvent;
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

    @KafkaListener(
            topics = KafkaConfig.RAW_PAYMENT_EVENTS_TOPIC,
            groupId = "reviveai-recovery-group"
    )
    public void consumePaymentEvent(String message) {

        try {

            log.info("Payment event received from Kafka");

            // ------------------------------------------------
            // 1. Deserialize Kafka message
            // ------------------------------------------------

            PaymentFailedEvent event =
                    objectMapper.readValue(
                            message,
                            PaymentFailedEvent.class
                    );

            // ------------------------------------------------
            // 2. Validate event structure
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
            // 3. Log payment failure
            // ------------------------------------------------

            log.info(
                    "Payment failure received. " +
                            "PaymentId: {}, CustomerId: {}, " +
                            "Amount: {}, Currency: {}, ErrorCode: {}",
                    payment.getId(),
                    payment.getCustomerId(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getErrorCode()
            );

            // ------------------------------------------------
            // 4. Send event to recovery service
            // ------------------------------------------------

            paymentRecoveryService.processPaymentFailure(event);

            log.info(
                    "Payment recovery processing completed. paymentId={}",
                    payment.getId()
            );

        } catch (Exception exception) {

            log.error(
                    "Failed to process payment event from Kafka",
                    exception
            );

            throw new RuntimeException(
                    "Failed to process payment event",
                    exception
            );
        }
    }
}