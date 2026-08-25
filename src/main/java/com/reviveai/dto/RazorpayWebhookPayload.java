package com.reviveai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class RazorpayWebhookPayload {

    private String event;

    @JsonProperty("created_at")
    private long createdAt;

    private PayloadDetails payload;

    @Data
    public static class PayloadDetails {
        private PaymentWrapper payment;
        private SubscriptionWrapper subscription;
    }

    @Data
    public static class PaymentWrapper {
        private PaymentEntity entity;
    }

    @Data
    public static class SubscriptionWrapper {
        private SubscriptionEntity entity;
    }

    @Data
    public static class PaymentEntity {
        private String id;
        private long amount;
        private String currency;
        private String status;

        @JsonProperty("order_id")
        private String orderId;

        @JsonProperty("subscription_id")
        private String subscriptionId;

        @JsonProperty("customer_id")
        private String customerId;

        @JsonProperty("error_code")
        private String errorCode;

        @JsonProperty("error_description")
        private String errorDescription;
    }

    @Data
    public static class SubscriptionEntity {
        private String id;
        private String status;

        @JsonProperty("customer_id")
        private String customerId;
    }
}