package com.reviveai.recovery;

import com.reviveai.entity.RecoveryAction;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.repository.RecoveryActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecoveryActionExecutor {

    private final RecoveryActionRepository recoveryActionRepository;

    public void execute(
            RecoveryCase recoveryCase,
            RecoveryDecision decision
    ) {

        if (recoveryCase == null) {

            throw new IllegalArgumentException(
                    "Recovery case cannot be null"
            );
        }

        if (decision == null) {

            throw new IllegalArgumentException(
                    "Recovery decision cannot be null"
            );
        }

        RecoveryStrategy strategy =
                decision.getStrategy();

        if (strategy == null) {

            log.warn(
                    "Recovery decision does not contain a strategy. recoveryCaseId={}",
                    recoveryCase.getId()
            );

            return;
        }

        log.info(
                "Executing recovery action. " +
                        "recoveryCaseId={}, strategy={}, priority={}, score={}",
                recoveryCase.getId(),
                strategy,
                decision.getPriority(),
                decision.getRecoveryScore()
        );

        RecoveryAction recoveryAction =
                RecoveryAction.builder()
                        .recoveryCase(recoveryCase)
                        .strategy(strategy)
                        .priority(decision.getPriority())
                        .recoveryScore(
                                decision.getRecoveryScore()
                        )
                        .status(
                                RecoveryAction.ActionStatus.PENDING
                        )
                        .reason(decision.getReason())
                        .build();

        recoveryAction =
                recoveryActionRepository.save(
                        recoveryAction
                );

        log.info(
                "Recovery action created. actionId={}, recoveryCaseId={}",
                recoveryAction.getId(),
                recoveryCase.getId()
        );

        try {

            executeAction(decision);

            recoveryAction.setStatus(
                    RecoveryAction.ActionStatus.EXECUTED
            );

            recoveryAction.setExecutedAt(
                    LocalDateTime.now()
            );

            recoveryActionRepository.save(
                    recoveryAction
            );

            log.info(
                    "Recovery action executed successfully. " +
                            "actionId={}, strategy={}",
                    recoveryAction.getId(),
                    strategy
            );

        } catch (Exception exception) {

            recoveryAction.setStatus(
                    RecoveryAction.ActionStatus.FAILED
            );

            recoveryActionRepository.save(
                    recoveryAction
            );

            log.error(
                    "Recovery action execution failed. " +
                            "actionId={}, strategy={}",
                    recoveryAction.getId(),
                    strategy,
                    exception
            );

            throw exception;
        }
    }

    private void executeAction(
            RecoveryDecision decision
    ) {

        switch (decision.getStrategy()) {

            case RETRY_PAYMENT ->
                    retryPayment(decision);

            case UPDATE_PAYMENT_METHOD ->
                    updatePaymentMethod(decision);

            case CUSTOMER_ACTION_REQUIRED ->
                    requestCustomerAction(decision);

            case MANUAL_REVIEW ->
                    sendForManualReview(decision);

            default ->
                    throw new IllegalStateException(
                            "Unsupported recovery strategy: "
                                    + decision.getStrategy()
                    );
        }
    }

    private void retryPayment(
            RecoveryDecision decision
    ) {

        log.info(
                "Payment retry action selected. " +
                        "priority={}, score={}",
                decision.getPriority(),
                decision.getRecoveryScore()
        );

        // Actual payment gateway retry will be implemented later.
    }

    private void updatePaymentMethod(
            RecoveryDecision decision
    ) {

        log.info(
                "Payment method update required. " +
                        "priority={}, score={}",
                decision.getPriority(),
                decision.getRecoveryScore()
        );

        // Payment-method update flow will be implemented later.
    }

    private void requestCustomerAction(
            RecoveryDecision decision
    ) {

        log.info(
                "Customer action required. " +
                        "priority={}, score={}",
                decision.getPriority(),
                decision.getRecoveryScore()
        );

        // Customer notification flow will be implemented later.
    }

    private void sendForManualReview(
            RecoveryDecision decision
    ) {

        log.info(
                "Recovery case sent for manual review. " +
                        "priority={}, score={}",
                decision.getPriority(),
                decision.getRecoveryScore()
        );

        // Manual review workflow will be implemented later.
    }


}
