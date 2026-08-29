package com.reviveai.recovery;

import com.reviveai.entity.RecoveryAction;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.repository.RecoveryActionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
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

        /*
         * Register all recovery action handlers.
         *
         * Example:
         *
         * RETRY_PAYMENT
         *      -> RetryPaymentHandler
         *
         * UPDATE_PAYMENT_METHOD
         *      -> UpdatePaymentMethodHandler
         *
         * CUSTOMER_ACTION_REQUIRED
         *      -> CustomerActionRequiredHandler
         *
         * MANUAL_REVIEW
         *      -> ManualReviewHandler
         */
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
        // 3. VALIDATE DECISION WITH SAFETY GUARD
        // =====================================================

        RecoveryDecisionGuard.GuardResult guardResult =
                recoveryDecisionGuard.validate(
                        recoveryCase,
                        decision
                );

        if (!guardResult.isAllowed()) {

            log.warn(
                    "Recovery decision rejected by guard. " +
                            "recoveryCaseId={}, strategy={}, reason={}",
                    recoveryCase.getId(),
                    decision.getStrategy(),
                    guardResult.getReason()
            );

            /*
             * Do not execute an unsafe decision.
             *
             * The caller is responsible for handling
             * the ESCALATED state.
             */

            return;
        }


        // =====================================================
        // 4. EXTRACT STRATEGY
        // =====================================================

        RecoveryStrategy strategy =
                decision.getStrategy();


        // =====================================================
        // 5. DEFENSIVE STRATEGY VALIDATION
        // =====================================================

        if (strategy == null) {

            log.warn(
                    "Recovery decision does not contain a strategy. " +
                            "recoveryCaseId={}",
                    recoveryCase.getId()
            );

            return;
        }


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


            // =================================================
            // 6A. ALREADY EXECUTED
            // =================================================

            if (action.getStatus()
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

            if (action.getStatus()
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

            /*
             * FAILED actions are allowed to create another
             * recovery attempt.
             */

            log.info(
                    "Previous recovery action failed. " +
                            "Creating a new recovery attempt. " +
                            "previousActionId={}, recoveryCaseId={}, strategy={}",
                    action.getId(),
                    recoveryCase.getId(),
                    strategy
            );
        }


        // =====================================================
        // 7. CREATE RECOVERY ACTION
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
                        "actionId={}, recoveryCaseId={}, strategy={}",
                recoveryAction.getId(),
                recoveryCase.getId(),
                strategy
        );


        // =====================================================
        // 8. EXECUTE STRATEGY-SPECIFIC HANDLER
        // =====================================================

        try {

            executeAction(
                    recoveryCase,
                    decision
            );


            // =================================================
            // 9. MARK ACTION AS EXECUTED
            // =================================================

            recoveryAction.setStatus(
                    RecoveryAction.ActionStatus.EXECUTED
            );

            recoveryAction.setExecutedAt(
                    LocalDateTime.now()
            );

            recoveryActionRepository.save(
                    recoveryAction
            );


            // =================================================
            // 10. MOVE RECOVERY CASE TO IN_PROGRESS
            // =================================================

            /*
             * The recovery action was successfully dispatched.
             *
             * It is NOT yet marked RECOVERED because the actual
             * payment result has not been received.
             *
             * Example:
             *
             * RetryPaymentHandler
             *       ↓
             * Razorpay retry request
             *       ↓
             * Payment webhook
             *       ↓
             * RECOVERED / FAILED
             */

            recoveryCase.setStatus(
                    RecoveryCase.RecoveryStatus.IN_PROGRESS
            );


            log.info(
                    "Recovery case moved to IN_PROGRESS. " +
                            "recoveryCaseId={}, strategy={}",
                    recoveryCase.getId(),
                    strategy
            );


            log.info(
                    "Recovery action executed successfully. " +
                            "actionId={}, recoveryCaseId={}, strategy={}",
                    recoveryAction.getId(),
                    recoveryCase.getId(),
                    strategy
            );

        } catch (Exception exception) {

            // =================================================
            // 11. MARK ACTION AS FAILED
            // =================================================

            recoveryAction.setStatus(
                    RecoveryAction.ActionStatus.FAILED
            );

            recoveryActionRepository.save(
                    recoveryAction
            );


            // =================================================
            // 12. MARK RECOVERY CASE AS FAILED
            // =================================================

            recoveryCase.setStatus(
                    RecoveryCase.RecoveryStatus.FAILED
            );


            log.error(
                    "Recovery action execution failed. " +
                            "actionId={}, recoveryCaseId={}, strategy={}",
                    recoveryAction.getId(),
                    recoveryCase.getId(),
                    strategy,
                    exception
            );


            /*
             * Propagate the exception so the caller knows
             * that recovery execution failed.
             */

            throw exception;
        }
    }


    // =========================================================
    // EXECUTE STRATEGY HANDLER
    // =========================================================

    /**
     * Finds the handler registered for the selected recovery
     * strategy and delegates execution to it.
     */
    private void executeAction(
            RecoveryCase recoveryCase,
            RecoveryDecision decision
    ) {

        RecoveryStrategy strategy =
                decision.getStrategy();


        // =====================================================
        // FIND HANDLER
        // =====================================================

        RecoveryActionHandler handler =
                handlers.get(strategy);


        // =====================================================
        // VALIDATE HANDLER
        // =====================================================

        if (handler == null) {

            throw new IllegalStateException(
                    "No recovery action handler registered for strategy: "
                            + strategy
            );
        }


        log.info(
                "Executing recovery action handler. " +
                        "strategy={}, recoveryCaseId={}, handler={}",
                strategy,
                recoveryCase.getId(),
                handler.getClass().getSimpleName()
        );


        // =====================================================
        // EXECUTE HANDLER
        // =====================================================

        handler.handle(
                recoveryCase,
                decision
        );
    }
}

