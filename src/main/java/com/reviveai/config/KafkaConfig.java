package com.reviveai.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    // ============================================================
    // TOPIC NAMES
    // ============================================================

    public static final String RAW_PAYMENT_EVENTS_TOPIC =
            "payment.events.raw";

    public static final String RECOVERY_CASES_TOPIC =
            "recovery.cases.created";

    public static final String RECOVERY_ACTIONS_TOPIC =
            "recovery.actions";

    public static final String PAYMENT_DLQ_TOPIC =
            "payment.events.dlq";

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // ============================================================
    // TOPICS
    // ============================================================

    /**
     * Raw payment events received from the payment provider.
     */
    @Bean
    public NewTopic rawPaymentEventsTopic() {

        return TopicBuilder
                .name(RAW_PAYMENT_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Recovery cases created after payment failure analysis.
     */
    @Bean
    public NewTopic recoveryCasesTopic() {

        return TopicBuilder
                .name(RECOVERY_CASES_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Recovery actions that are ready for asynchronous execution.
     */
    @Bean
    public NewTopic recoveryActionsTopic() {

        return TopicBuilder
                .name(RECOVERY_ACTIONS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Dead-letter topic for payment events that cannot be processed.
     */
    @Bean
    public NewTopic paymentDlqTopic() {

        return TopicBuilder
                .name(PAYMENT_DLQ_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }

    // ============================================================
    // PRODUCER
    // ============================================================

    @Bean
    public ProducerFactory<String, String> producerFactory() {

        Map<String, Object> configProps =
                new HashMap<>();

        configProps.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );

        configProps.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );

        configProps.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );

        return new DefaultKafkaProducerFactory<>(
                configProps
        );
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(
            ProducerFactory<String, String> producerFactory
    ) {

        return new KafkaTemplate<>(
                producerFactory
        );
    }

    // ============================================================
    // CONSUMER
    // ============================================================

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {

        Map<String, Object> configProps =
                new HashMap<>();

        configProps.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );

        configProps.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "reviveai-recovery-group"
        );

        configProps.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );

        configProps.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );

        configProps.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );

        return new DefaultKafkaConsumerFactory<>(
                configProps
        );
    }

    // ============================================================
    // KAFKA LISTENER CONTAINER
    // ============================================================

    @Bean(name = "kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String>
    kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory
    ) {

        ConcurrentKafkaListenerContainerFactory<String, String>
                factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(
                consumerFactory
        );

        return factory;
    }
}