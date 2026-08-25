package com.reviveai.service;

import com.reviveai.dto.PaymentFailedEvent;

public interface RecoveryService {

    void processPaymentFailure(PaymentFailedEvent event);
}