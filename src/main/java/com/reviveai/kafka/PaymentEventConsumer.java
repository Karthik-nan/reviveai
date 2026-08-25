package com.reviveai.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviveai.config.KafkaConfig;
import com.reviveai.dto.PaymentFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = KafkaConfig.RAW_PAYMENT_EVENTS_TOPIC,
            groupId = "reviveai-recovery-group"
    )
    public void consumePaymentEvent(String message) {

        try {

            log.info("Payment event received from Kafka");

            PaymentFailedEvent event =
                    objectMapper.readValue(
                            message,
                            PaymentFailedEvent.class
                    );

            if (event.getPayload() == null ||
                    event.getPayload().getPayment() == null) {

                log.warn("Invalid payment event: payment data is missing");
                return;
            }

            PaymentFailedEvent.Payment payment =
                    event.getPayload().getPayment();

            log.info(
                    "Payment failure received. " +
                            "PaymentId: {}, CustomerId: {}, Amount: {}, Currency: {}, ErrorCode: {}",
                    payment.getId(),
                    payment.getCustomerId(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getErrorCode()
            );

            /*
             * NEXT STEP:
             *
             * Send this payment failure to the
             * recovery service.
             */

        } catch (Exception exception) {

            log.error(
                    "Failed to process payment event from Kafka",
                    exception
            );
        }
    }
}

