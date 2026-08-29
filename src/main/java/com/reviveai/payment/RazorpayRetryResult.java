package com.reviveai.payment;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RazorpayRetryResult {

    private final RetryStatus status;

    private final String paymentId;

    private final String message;

    public enum RetryStatus {

        SUBMITTED,

        FAILED
    }
}
