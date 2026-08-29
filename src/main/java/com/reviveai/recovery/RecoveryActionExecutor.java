package com.reviveai.recovery;

import com.reviveai.entity.RecoveryAction;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.repository.RecoveryActionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RecoveryActionExecutor {

    private final RecoveryActionRepository recoveryActionRepository;

    private final RecoveryDecisionGuard recoveryDecisionGuard;

    private final Map<RecoveryStrategy, RecoveryActionHandler> handlers;

    public RecoveryActionExecutor(
            RecoveryActionRepository recoveryActionRepository,
            List<RecoveryActionHandler> handlerList,
            RecoveryDecisionGuard recoveryDecisionGuard
    ) {

        this.recoveryActionRepository =
                recoveryActionRepository;

        this.recoveryDecisionGuard =
                recoveryDecisionGuard;

        this.handlers =
                handlerList.stream()
                        .collect(Collectors.toMap(
                                RecoveryActionHandler::getStrategy,
                                Function.identity()
                        ));

        log.info(
                "Recovery action handlers registered. strategies={}",
                handlers.keySet()
        );
    }

    // =========================================================
    // EXECUTE RECOVERY ACTION
    // =========================================================

    @Transactional
    public void execute(
            RecoveryCase recoveryCase,
            RecoveryDecision decision
    ) {

        // =====================================================
        // 1. VALIDATE RECOVERY CASE
        // =====================================================

        if (recoveryCase == null) {

            throw new IllegalArgumentException(
                    "Recovery case cannot be null"
            );
        }

        // =====================================================
        // 2. VALIDATE DECISION
        // =====================================================

        if (decision == null) {

            throw new IllegalArgumentException(
                    "Recovery decision cannot be null"
            );
        }

        // =====================================================
        // 3. SAFETY GUARD
        // =====================================================

        /*
         * IMPORTANT:
         *
         * The guard is intentionally called BEFORE checking
         * whether the strategy is null.
         *
         * This ensures every recovery decision passes through
         * the central safety boundary.
         */

        RecoveryDecisionGuard.GuardResult guardResult;

        try {

            guardResult =
                    recoveryDecisionGuard.validate(
                            recoveryCase,
                            decision
                    );

        } catch (Exception exception) {

            log.error(
                    "Recovery decision guard threw an exception. " +
                            "recoveryCaseId={}",
                    recoveryCase.getId(),
                    exception
            );

            recoveryCase.setStatus(
                    RecoveryCase.RecoveryStatus.ESCALATED
            );

            return;
        }

        if (guardResult == null) {

            log.error(
                    "Recovery decision guard returned null. " +
                            "recoveryCaseId={}",
                    recoveryCase.getId()
            );

            recoveryCase.setStatus(
                    RecoveryCase.RecoveryStatus.ESCALATED
            );

            return;
        }

        if (!guardResult.isAllowed()) {

            log.warn(
                    "Recovery decision rejected by guard. " +
                            "recoveryCaseId={}, strategy={}, reason={}",
                    recoveryCase.getId(),
                    decision.getStrategy(),
                    guardResult.getReason()
            );

            recoveryCase.setStatus(
                    RecoveryCase.RecoveryStatus.ESCALATED
            );

            return;
        }

        // =====================================================
        // 4. VALIDATE STRATEGY
        // =====================================================

        RecoveryStrategy strategy =
                decision.getStrategy();

        if (strategy == null) {

            /*
             * The guard has already validated the decision.
             *
             * A null strategy cannot be executed, so simply
             * stop without creating a RecoveryAction.
             */

            log.warn(
                    "Recovery decision does not contain a strategy. " +
                            "recoveryCaseId={}",
                    recoveryCase.getId()
            );

            return;
        }

        // =====================================================
        // 5. LOG DECISION
        // =====================================================

        log.info(
                "Preparing recovery action. " +
                        "recoveryCaseId={}, strategy={}, priority={}, score={}",
                recoveryCase.getId(),
                strategy,
                decision.getPriority(),
                decision.getRecoveryScore()
        );

        // =====================================================
        // 6. FIND EXISTING ACTION
        // =====================================================

        Optional<RecoveryAction> existingAction =
                recoveryActionRepository
                        .findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                                recoveryCase.getId(),
                                strategy
                        );

        if (existingAction.isPresent()) {

            RecoveryAction action =
                    existingAction.get();

            RecoveryAction.ActionStatus status =
                    action.getStatus();

            // =================================================
            // 6A. ALREADY EXECUTED
            // =================================================

            if (status
                    == RecoveryAction.ActionStatus.EXECUTED) {

                log.info(
                        "Recovery action already executed. " +
                                "Skipping duplicate execution. " +
                                "actionId={}, recoveryCaseId={}, strategy={}",
                        action.getId(),
                        recoveryCase.getId(),
                        strategy
                );

                return;
            }

            // =================================================
            // 6B. ALREADY PENDING
            // =================================================

            if (status
                    == RecoveryAction.ActionStatus.PENDING) {

                log.info(
                        "Recovery action already pending. " +
                                "Skipping duplicate execution. " +
                                "actionId={}, recoveryCaseId={}, strategy={}",
                        action.getId(),
                        recoveryCase.getId(),
                        strategy
                );

                return;
            }

            // =================================================
            // 6C. PREVIOUS ACTION FAILED
            // =================================================

            if (status
                    == RecoveryAction.ActionStatus.FAILED) {

                log.info(
                        "Previous recovery action failed. " +
                                "Creating a new recovery attempt. " +
                                "previousActionId={}, recoveryCaseId={}, strategy={}",
                        action.getId(),
                        recoveryCase.getId(),
                        strategy
                );
            }
        }

        // =====================================================
        // 7. FIND HANDLER
        // =====================================================

        RecoveryActionHandler handler =
                handlers.get(strategy);

        if (handler == null) {

            log.error(
                    "No recovery action handler registered. " +
                            "recoveryCaseId={}, strategy={}",
                    recoveryCase.getId(),
                    strategy
            );

            recoveryCase.setStatus(
                    RecoveryCase.RecoveryStatus.ESCALATED
            );

            return;
        }

        // =====================================================
        // 8. CREATE PENDING ACTION
        // =====================================================

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
                        .reason(
                                decision.getReason()
                        )
                        .build();

        recoveryAction =
                recoveryActionRepository.save(
                        recoveryAction
                );

        log.info(
                "Recovery action created. " +
                        "actionId={}, recoveryCaseId={}, strategy={}, " +
                        "priority={}, score={}",
                recoveryAction.getId(),
                recoveryCase.getId(),
                strategy,
                decision.getPriority(),
                decision.getRecoveryScore()
        );

        // =====================================================
        // 9. EXECUTE HANDLER
        // =====================================================

        RecoveryOutcome outcome;

        try {

            log.info(
                    "Executing recovery action handler. " +
                            "strategy={}, recoveryCaseId={}, handler={}",
                    strategy,
                    recoveryCase.getId(),
                    handler.getClass().getSimpleName()
            );

            outcome =
                    handler.handle(
                            recoveryCase,
                            decision
                    );

        } catch (Exception exception) {

            // =================================================
            // 9A. HANDLER EXCEPTION
            // =================================================

            recoveryAction.setStatus(
                    RecoveryAction.ActionStatus.FAILED
            );

            recoveryActionRepository.save(
                    recoveryAction
            );

            recoveryCase.setStatus(
                    RecoveryCase.RecoveryStatus.FAILED
            );

            log.error(
                    "Recovery action handler threw an exception. " +
                            "actionId={}, recoveryCaseId={}, strategy={}",
                    recoveryAction.getId(),
                    recoveryCase.getId(),
                    strategy,
                    exception
            );

            throw exception;
        }

        // =====================================================
        // 10. VALIDATE OUTCOME
        // =====================================================

        if (outcome == null) {

            recoveryAction.setStatus(
                    RecoveryAction.ActionStatus.FAILED
            );

            recoveryActionRepository.save(
                    recoveryAction
            );

            recoveryCase.setStatus(
                    RecoveryCase.RecoveryStatus.FAILED
            );

            throw new IllegalStateException(
                    "Recovery action handler returned null outcome. " +
                            "recoveryCaseId=" +
                            recoveryCase.getId() +
                            ", strategy=" +
                            strategy
            );
        }

        // =====================================================
        // 11. READ OUTCOME
        // =====================================================

        RecoveryOutcome.OutcomeStatus outcomeStatus =
                outcome.getStatus();

        BigDecimal amountRecovered =
                outcome.getAmountRecovered();

        if (amountRecovered == null) {

            amountRecovered =
                    BigDecimal.ZERO;
        }

        log.info(
                "Recovery outcome received. " +
                        "actionId={}, recoveryCaseId={}, strategy={}, " +
                        "status={}, amountRecovered={}, reason={}",
                recoveryAction.getId(),
                recoveryCase.getId(),
                strategy,
                outcomeStatus,
                amountRecovered,
                outcome.getReason()
        );

        // =====================================================
        // 12. RECOVERED
        // =====================================================

        if (outcomeStatus
                == RecoveryOutcome.OutcomeStatus.RECOVERED) {

            recoveryAction.setStatus(
                    RecoveryAction.ActionStatus.EXECUTED
            );

            recoveryAction.setExecutedAt(
                    LocalDateTime.now()
            );

            recoveryActionRepository.save(
                    recoveryAction
            );

            recoveryCase.setStatus(
                    RecoveryCase.RecoveryStatus.RECOVERED
            );

            recoveryCase.setAmountRecovered(
                    amountRecovered
            );

            recoveryCase.setResolvedAt(
                    OffsetDateTime.now()
            );

            log.info(
                    "Recovery case marked RECOVERED. " +
                            "actionId={}, recoveryCaseId={}, strategy={}, " +
                            "amountRecovered={}",
                    recoveryAction.getId(),
                    recoveryCase.getId(),
                    strategy,
                    amountRecovered
            );

            return;
        }

        // =====================================================
        // 13. SUBMITTED
        // =====================================================

        if (outcomeStatus
                == RecoveryOutcome.OutcomeStatus.SUBMITTED) {

            /*
             * The recovery action was successfully submitted.
             *
             * The payment itself is NOT necessarily recovered yet.
             *
             * Example:
             *
             * RetryPaymentHandler
             *        ↓
             * Retry submitted
             *        ↓
             * Razorpay processes payment
             *        ↓
             * payment.captured webhook
             *        ↓
             * RecoveryCase -> RECOVERED
             */

            recoveryAction.setStatus(
                    RecoveryAction.ActionStatus.EXECUTED
            );

            recoveryAction.setExecutedAt(
                    LocalDateTime.now()
            );

            recoveryActionRepository.save(
                    recoveryAction
            );

            recoveryCase.setStatus(
                    RecoveryCase.RecoveryStatus.IN_PROGRESS
            );

            log.info(
                    "Recovery action submitted successfully. " +
                            "Action marked EXECUTED while recovery case " +
                            "remains IN_PROGRESS awaiting payment webhook. " +
                            "actionId={}, recoveryCaseId={}, strategy={}, reason={}",
                    recoveryAction.getId(),
                    recoveryCase.getId(),
                    strategy,
                    outcome.getReason()
            );

            return;
        }

        // =====================================================
        // 14. FAILED OUTCOME
        // =====================================================

        if (outcomeStatus
                == RecoveryOutcome.OutcomeStatus.FAILED) {

            recoveryAction.setStatus(
                    RecoveryAction.ActionStatus.FAILED
            );

            recoveryActionRepository.save(
                    recoveryAction
            );

            recoveryCase.setStatus(
                    RecoveryCase.RecoveryStatus.FAILED
            );

            log.warn(
                    "Recovery action failed. " +
                            "Action marked FAILED. " +
                            "recoveryCaseId={}, actionId={}, strategy={}, reason={}",
                    recoveryCase.getId(),
                    recoveryAction.getId(),
                    strategy,
                    outcome.getReason()
            );

            return;
        }

        // =====================================================
        // 15. UNKNOWN OUTCOME
        // =====================================================

        recoveryAction.setStatus(
                RecoveryAction.ActionStatus.FAILED
        );

        recoveryActionRepository.save(
                recoveryAction
        );

        recoveryCase.setStatus(
                RecoveryCase.RecoveryStatus.FAILED
        );

        throw new IllegalStateException(
                "Unsupported recovery outcome status: "
                        + outcomeStatus
        );
    }
}
