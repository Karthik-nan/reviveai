package com.reviveai.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

//@Service
public class PaymentEventProducer {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentEventProducer.class);

    private static final String TOPIC = "payment.events.raw";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public PaymentEventProducer(
            KafkaTemplate<String, String> kafkaTemplate) {

        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishPaymentEvent(String message) {

        kafkaTemplate.send(TOPIC, message)
                .whenComplete((result, exception) -> {

                    if (exception != null) {

                        log.error(
                                "Failed to publish payment event to Kafka. Topic: {}",
                                TOPIC,
                                exception
                        );

                        return;
                    }

                    if (result == null) {

                        log.error(
                                "Kafka returned a null result while publishing payment event"
                        );

                        return;
                    }

                    log.info(
                            "Payment event published successfully. " +
                                    "Topic: {}, Partition: {}, Offset: {}",
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset()
                    );
                });
    }
}

