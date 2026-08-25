package com.reviveai.service;

import com.reviveai.dto.RazorpayWebhookPayload;
import com.reviveai.entity.RecoveryCase;

public interface SubscriptionHealthService {
    /**
     * Ingests raw payment failure payload, creates database records,
     * calculates initial risk, and opens a RecoveryCase.
     */
    RecoveryCase processPaymentFailure(RazorpayWebhookPayload payload);
}