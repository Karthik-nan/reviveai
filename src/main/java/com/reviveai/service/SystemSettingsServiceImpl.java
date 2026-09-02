package com.reviveai.service;

import com.reviveai.dto.SystemSettingsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SystemSettingsServiceImpl
        implements SystemSettingsService {

    @Value("${spring.application.name:reviveai}")
    private String applicationName;

    @Value("${server.port:8080}")
    private int backendPort;

    @Value("${spring.datasource.url:unknown}")
    private String databaseUrl;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String kafka;

    @Value("${spring.kafka.consumer.group-id:reviveai-recovery-group}")
    private String kafkaConsumerGroup;

    @Value("${razorpay.webhook.secret:}")
    private String razorpayWebhookSecret;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String aiProvider;

    @Value("${spring.ai.ollama.chat.options.model:codellama}")
    private String chatModel;

    @Value("${spring.ai.ollama.embedding.model:nomic-embed-text}")
    private String embeddingModel;

    @Value("${spring.ai.vectorstore.pgvector.table-name:policy_vectors}")
    private String vectorStore;

    @Value("${spring.jackson.time-zone:UTC}")
    private String timezone;

    @Value("${spring.profiles.active:default}")
    private String environment;

    @Override
    public SystemSettingsResponse getSystemSettings() {

        return SystemSettingsResponse.builder()
                .applicationName(applicationName)
                .backendPort(backendPort)
                .database(formatDatabase(databaseUrl))
                .redis(redisHost + ":" + redisPort)
                .kafka(kafka)
                .kafkaConsumerGroup(kafkaConsumerGroup)
                .razorpayWebhook(
                        razorpayWebhookSecret.isBlank()
                                ? "Not configured"
                                : "Configured"
                )
                .aiProvider(aiProvider)
                .chatModel(chatModel)
                .embeddingModel(embeddingModel)
                .vectorStore(vectorStore)
                .timezone(timezone)
                .environment(environment)
                .build();
    }

    private String formatDatabase(String url) {

        if (url == null || url.isBlank()) {
            return "Not configured";
        }

        return url
                .replaceFirst(
                        "^jdbc:postgresql://",
                        ""
                )
                .replaceFirst(
                        "/.*$",
                        ""
                );
    }
}
