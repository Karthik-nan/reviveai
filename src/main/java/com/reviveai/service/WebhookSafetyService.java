package com.reviveai.service;

public interface WebhookSafetyService {
    /**
     * Validates incoming HMAC SHA256 signature from Razorpay webhooks.
     */
    boolean isValidSignature(String payload, String signature, String secret);

    /**
     * Atomically acquires a Redis key lock to ensure duplicate events are dropped.
     */
    boolean acquireIdempotencyLock(String eventId);
}