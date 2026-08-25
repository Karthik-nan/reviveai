package com.reviveai.service;

import com.reviveai.entity.RecoveryCase;
import com.reviveai.dto.PaymentFailedEvent;

public interface RecoveryCaseService {

    RecoveryCase createRecoveryCase(
            PaymentFailedEvent.Payment payment
    );
}