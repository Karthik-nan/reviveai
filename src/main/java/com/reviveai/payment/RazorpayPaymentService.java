package com.reviveai.payment;

import com.reviveai.entity.PaymentAttempt;

public interface RazorpayPaymentService {

    /**
     * Initiates a retry flow for an existing payment attempt.
     *
     * The actual payment result may be asynchronous.
     */
    RazorpayRetryResult retryPayment(
            PaymentAttempt paymentAttempt
    );
}