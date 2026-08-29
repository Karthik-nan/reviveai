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

            RecoveryOutcome outcome =
                    executeAction(
                            recoveryCase,
                            decision
                    );

            // =================================================
            // 9. VALIDATE OUTCOME
            // =================================================

            if (outcome == null) {

                throw new IllegalStateException(
                        "Recovery action handler returned null outcome. " +
                                "recoveryCaseId=" + recoveryCase.getId() +
                                ", strategy=" + strategy
                );
            }

            log.info(
                    "Recovery outcome received. " +
                            "recoveryCaseId={}, strategy={}, status={}, " +
                            "amountRecovered={}, reason={}",
                    recoveryCase.getId(),
                    strategy,
                    outcome.getStatus(),
                    outcome.getAmountRecovered(),
                    outcome.getReason()
            );

            // =================================================
            // 10. MARK ACTION AS EXECUTED
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
            // 11. HANDLE RECOVERY OUTCOME
            // =================================================

            /*
             * IMPORTANT:
             *
             * The action being executed successfully does NOT
             * necessarily mean the payment was recovered.
             *
             * For example:
             *
             * RetryPaymentHandler
             *       ↓
             * Retry request submitted
             *       ↓
             * Payment provider processes request
             *       ↓
             * Webhook
             *       ↓
             * RECOVERED / FAILED
             *
             * Therefore the current retry handler leaves the
             * recovery case IN_PROGRESS.
             */

            if (outcome.getStatus()
                    == RecoveryOutcome.OutcomeStatus.RECOVERED) {

                recoveryCase.setStatus(
                        RecoveryCase.RecoveryStatus.RECOVERED
                );

                log.info(
                        "Recovery case marked RECOVERED. " +
                                "recoveryCaseId={}, strategy={}, " +
                                "amountRecovered={}",
                        recoveryCase.getId(),
                        strategy,
                        outcome.getAmountRecovered()
                );

            } else if (outcome.getStatus()
                    == RecoveryOutcome.OutcomeStatus.SUBMITTED) {

                /*
                 * The recovery action was successfully submitted,
                 * but the payment provider has not returned the
                 * final payment result yet.
                 *
                 * The RecoveryCase remains IN_PROGRESS until a
                 * payment-success or payment-failure event is received.
                 */

                recoveryCase.setStatus(
                        RecoveryCase.RecoveryStatus.IN_PROGRESS
                );

                log.info(
                        "Recovery action submitted. " +
                                "Recovery case remains IN_PROGRESS " +
                                "awaiting payment provider result. " +
                                "recoveryCaseId={}, strategy={}, reason={}",
                        recoveryCase.getId(),
                        strategy,
                        outcome.getReason()
                );

            } else if (outcome.getStatus()
                    == RecoveryOutcome.OutcomeStatus.FAILED) {

                /*
                 * This represents an actual recovery failure.
                 */

                recoveryCase.setStatus(
                        RecoveryCase.RecoveryStatus.FAILED
                );

                log.info(
                        "Recovery action failed. " +
                                "recoveryCaseId={}, strategy={}, reason={}",
                        recoveryCase.getId(),
                        strategy,
                        outcome.getReason()
                );
            }
            log.info(
                    "Recovery action execution completed. " +
                            "actionId={}, recoveryCaseId={}, strategy={}",
                    recoveryAction.getId(),
                    recoveryCase.getId(),
                    strategy
            );

        } catch (Exception exception) {

            // =================================================
            // 12. MARK ACTION AS FAILED
            // =================================================

            recoveryAction.setStatus(
                    RecoveryAction.ActionStatus.FAILED
            );

            recoveryActionRepository.save(
                    recoveryAction
            );

            // =================================================
            // 13. MARK RECOVERY CASE AS FAILED
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
     *
     * @return outcome returned by the strategy handler
     */
    private RecoveryOutcome executeAction(
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

        return handler.handle(
                recoveryCase,
                decision
        );
    }
}