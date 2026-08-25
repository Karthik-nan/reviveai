package com.reviveai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviveai.config.KafkaConfig;
import com.reviveai.dto.RazorpayWebhookPayload;
import com.reviveai.entity.RecoveryCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final SubscriptionHealthService healthService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(
            topics = KafkaConfig.RAW_PAYMENT_EVENTS_TOPIC,
            groupId = "${spring.kafka.consumer.group-id:reviveai-recovery-group}"
    )
    public void consumeRawPaymentEvent(String message) {

        try {

            log.info(
                    "Kafka Consumed Raw Payment Event Payload: {}",
                    message
            );

            // ========================================================
            // Deserialize Kafka message
            // ========================================================

            RazorpayWebhookPayload payload =
                    objectMapper.readValue(
                            message,
                            RazorpayWebhookPayload.class
                    );

            // ========================================================
            // Process failed payment
            // ========================================================

            if (
                    payload.getEvent() != null
                            && payload.getEvent().contains("failed")
            ) {

                RecoveryCase createdCase =
                        healthService.processPaymentFailure(payload);

                // ====================================================
                // Publish Recovery Case event
                // ====================================================

                String caseEvent =
                        objectMapper.writeValueAsString(
                                new RecoveryCaseEvent(
                                        createdCase.getId().toString(),
                                        createdCase.getStatus().name()
                                )
                        );

                kafkaTemplate.send(
                        KafkaConfig.RECOVERY_CASES_TOPIC,
                        createdCase.getId().toString(),
                        caseEvent
                );

                log.info(
                        "Published RecoveryCase event. CaseId={}, Status={}",
                        createdCase.getId(),
                        createdCase.getStatus()
                );
            }

        } catch (Exception e) {

            log.error(
                    "Failed to process payment event from Kafka. Dead-lettering...",
                    e
            );

            kafkaTemplate.send(
                    KafkaConfig.PAYMENT_DLQ_TOPIC,
                    message
            );
        }
    }

    // ================================================================
    // Kafka Recovery Case Event
    // ================================================================

    private record RecoveryCaseEvent(
            String caseId,
            String status
    ) {
    }
}