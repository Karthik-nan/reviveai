package com.reviveai.service;

import com.reviveai.dto.PaymentFailedEvent;
import com.reviveai.dto.RecoveryActionResponse;
import com.reviveai.dto.RecoveryCaseResponse;
import com.reviveai.entity.RecoveryAction;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.repository.RecoveryActionRepository;
import com.reviveai.repository.RecoveryCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecoveryCaseServiceImpl
        implements RecoveryCaseService {

    private final RecoveryCaseRepository recoveryCaseRepository;

    private final RecoveryActionRepository recoveryActionRepository;

    @Override
    public RecoveryCase createRecoveryCase(
            PaymentFailedEvent.Payment payment
    ) {

        // Keep your existing recovery-case creation logic here.
        // We will connect this to the existing PaymentRecoveryService
        // logic rather than duplicate it.

        throw new UnsupportedOperationException(
                "createRecoveryCase implementation not connected yet"
        );
    }

    @Override
    public List<RecoveryCaseResponse> getAllRecoveryCases() {

        return recoveryCaseRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public RecoveryCaseResponse getRecoveryCaseById(
            UUID id
    ) {

        RecoveryCase recoveryCase =
                recoveryCaseRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Recovery case not found: " + id
                                )
                        );

        return toResponse(recoveryCase);
    }

    @Override
    public List<RecoveryActionResponse> getRecoveryActions(
            UUID recoveryCaseId
    ) {

        return recoveryActionRepository
                .findByRecoveryCaseId(recoveryCaseId)
                .stream()
                .map(this::toActionResponse)
                .toList();
    }

    private RecoveryCaseResponse toResponse(
            RecoveryCase recoveryCase
    ) {

        return RecoveryCaseResponse.builder()
                .id(recoveryCase.getId())
                .subscriptionId(
                        recoveryCase.getSubscription() != null
                                ? recoveryCase
                                .getSubscription()
                                .getId()
                                : null
                )
                .failedPaymentId(
                        recoveryCase.getFailedPayment() != null
                                ? recoveryCase
                                .getFailedPayment()
                                .getId()
                                : null
                )
                .status(
                        recoveryCase.getStatus()
                )
                .recoveryPotential(
                        recoveryCase
                                .getRecoveryPotential()
                )
                .recoveryScore(
                        recoveryCase
                                .getRecoveryScore()
                )
                .amountAtRisk(
                        recoveryCase
                                .getAmountAtRisk()
                )
                .amountRecovered(
                        recoveryCase
                                .getAmountRecovered()
                )
                .createdAt(
                        recoveryCase
                                .getCreatedAt()
                )
                .resolvedAt(
                        recoveryCase
                                .getResolvedAt()
                )
                .build();
    }

    private RecoveryActionResponse toActionResponse(
            RecoveryAction action
    ) {

        return RecoveryActionResponse.builder()
                .id(action.getId())
                .recoveryCaseId(
                        action.getRecoveryCase() != null
                                ? action
                                .getRecoveryCase()
                                .getId()
                                : null
                )
                .strategy(
                        action.getStrategy()
                )
                .priority(
                        action.getPriority()
                )
                .status(
                        action.getStatus()
                )
                .reason(
                        action.getReason()
                )
                .recoveryScore(
                        action.getRecoveryScore()
                )
                .createdAt(
                        action.getCreatedAt()
                )
                .executedAt(
                        action.getExecutedAt()
                )
                .build();
    }
}
