package com.reviveai.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SystemSettingsResponse {

    private String applicationName;

    private int backendPort;

    private String database;

    private String redis;

    private String kafka;

    private String kafkaConsumerGroup;

    private String razorpayWebhook;

    private String aiProvider;

    private String chatModel;

    private String embeddingModel;

    private String vectorStore;

    private String timezone;

    private String environment;
}