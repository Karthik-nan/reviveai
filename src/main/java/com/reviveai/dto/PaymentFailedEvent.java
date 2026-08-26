package com.reviveai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentFailedEvent {

    private String event;

    @JsonProperty("created_at")
    private Long createdAt;

    private Payload payload;

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Payload getPayload() {
        return payload;
    }

    public void setPayload(Payload payload) {
        this.payload = payload;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {

        private Payment payment;

        private Subscription subscription;

        public Payment getPayment() {
            return payment;
        }

        public void setPayment(Payment payment) {
            this.payment = payment;
        }

        public Subscription getSubscription() {
            return subscription;
        }

        public void setSubscription(Subscription subscription) {
            this.subscription = subscription;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payment {

        private Entity entity;

        public Entity getEntity() {
            return entity;
        }

        public void setEntity(Entity entity) {
            this.entity = entity;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Subscription {

        private SubscriptionEntity entity;

        public SubscriptionEntity getEntity() {
            return entity;
        }

        public void setEntity(SubscriptionEntity entity) {
            this.entity = entity;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Entity {

        private String id;
        private Long amount;
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

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Long getAmount() {
            return amount;
        }

        public void setAmount(Long amount) {
            this.amount = amount;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public String getSubscriptionId() {
            return subscriptionId;
        }

        public void setSubscriptionId(String subscriptionId) {
            this.subscriptionId = subscriptionId;
        }

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(String errorCode) {
            this.errorCode = errorCode;
        }

        public String getErrorDescription() {
            return errorDescription;
        }

        public void setErrorDescription(String errorDescription) {
            this.errorDescription = errorDescription;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubscriptionEntity {

        private String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}