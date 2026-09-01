package com.reviveai.service;

import com.reviveai.dto.PaymentFailedEvent;
import com.reviveai.dto.RecoveryActionResponse;
import com.reviveai.dto.RecoveryCaseResponse;
import com.reviveai.entity.RecoveryCase;

import java.util.List;
import java.util.UUID;

public interface RecoveryCaseService {

    RecoveryCase createRecoveryCase(
            PaymentFailedEvent.Payment payment
    );

    List<RecoveryCaseResponse> getAllRecoveryCases();

    RecoveryCaseResponse getRecoveryCaseById(
            UUID id
    );

    List<RecoveryActionResponse> getRecoveryActions(
            UUID recoveryCaseId
    );
}
