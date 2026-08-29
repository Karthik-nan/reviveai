package com.reviveai.service;

public interface WebhookSafetyService {

    /**
     * Validates incoming HMAC SHA256 signature
     * from Razorpay webhooks.
     */
    boolean isValidSignature(
            String payload,
            String signature,
            String secret
    );
}

