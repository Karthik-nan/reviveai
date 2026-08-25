package com.reviveai.recovery;

public enum RecoveryStrategy {

    RETRY_PAYMENT,

    UPDATE_PAYMENT_METHOD,

    CUSTOMER_ACTION_REQUIRED,

    MANUAL_REVIEW
}