package com.reviveai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentFailedEvent {

    // ========================================================
    // WEBHOOK EVENT
    // ========================================================

    /*
     * Razorpay sends the event ID in the HTTP header:
     *
     * X-Razorpay-Event-Id
     *
     * It is NOT normally part of the webhook JSON body.
     *
     * Therefore this field is populated by our controller /
     * Kafka pipeline rather than Jackson deserialization.
     */
    private String eventId;

    /*
     * Example:
     *
     * payment.failed
     */
    private String event;

    /*
     * Razorpay webhook creation timestamp.
     */
    @JsonProperty("created_at")
    private Long createdAt;

    /*
     * Main webhook payload.
     */
    private Payload payload;

    // ========================================================
    // GETTERS / SETTERS
    // ========================================================

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

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

    // ========================================================
    // PAYLOAD
    // ========================================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {

        private Payment payment;

        private Subscription subscription;

        private Customer customer;

        // ====================================================
        // GETTERS / SETTERS
        // ====================================================

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

        public Customer getCustomer() {
            return customer;
        }

        public void setCustomer(Customer customer) {
            this.customer = customer;
        }
    }

    // ========================================================
    // PAYMENT
    // ========================================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payment {

        private Entity entity;

        // ====================================================
        // GETTERS / SETTERS
        // ====================================================

        public Entity getEntity() {
            return entity;
        }

        public void setEntity(Entity entity) {
            this.entity = entity;
        }
    }

    // ========================================================
    // SUBSCRIPTION
    // ========================================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Subscription {

        private SubscriptionEntity entity;

        // ====================================================
        // GETTERS / SETTERS
        // ====================================================

        public SubscriptionEntity getEntity() {
            return entity;
        }

        public void setEntity(SubscriptionEntity entity) {
            this.entity = entity;
        }
    }

    // ========================================================
    // CUSTOMER
    // ========================================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Customer {

        private CustomerEntity entity;

        // ====================================================
        // GETTERS / SETTERS
        // ====================================================

        public CustomerEntity getEntity() {
            return entity;
        }

        public void setEntity(CustomerEntity entity) {
            this.entity = entity;
        }
    }

    // ========================================================
    // CUSTOMER ENTITY
    // ========================================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CustomerEntity {

        private String id;

        // ====================================================
        // GETTERS / SETTERS
        // ====================================================

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    // ========================================================
    // PAYMENT ENTITY
    // ========================================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Entity {

        /*
         * Razorpay payment ID.
         *
         * Example:
         * pay_xxxxxxxxxxxxxx
         */
        private String id;

        /*
         * Razorpay amount is represented in the smallest
         * currency unit.
         *
         * Example:
         * ₹499.00 -> 49900
         */
        private Long amount;

        /*
         * Example:
         * INR
         */
        private String currency;

        /*
         * Example:
         * failed
         */
        private String status;

        /*
         * Razorpay order ID.
         */
        @JsonProperty("order_id")
        private String orderId;

        /*
         * Razorpay subscription ID.
         */
        @JsonProperty("subscription_id")
        private String subscriptionId;

        /*
         * Razorpay customer ID.
         */
        @JsonProperty("customer_id")
        private String customerId;

        /*
         * Gateway failure code.
         */
        @JsonProperty("error_code")
        private String errorCode;

        /*
         * Gateway failure description.
         */
        @JsonProperty("error_description")
        private String errorDescription;

        // ====================================================
        // GETTERS / SETTERS
        // ====================================================

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

    // ========================================================
    // SUBSCRIPTION ENTITY
    // ========================================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubscriptionEntity {

        /*
         * Razorpay subscription ID.
         */
        private String id;

        /*
         * Subscription status.
         */
        private String status;

        // ====================================================
        // GETTERS / SETTERS
        // ====================================================

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}