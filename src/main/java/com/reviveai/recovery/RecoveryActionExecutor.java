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

    public void execute(
            RecoveryCase recoveryCase,
            RecoveryDecision decision
    ) {

        /*
         * Basic input validation.
         */

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

        /*
         * Recovery decision safety validation.
         *
         * The guard prevents unsafe automated recovery
         * decisions from reaching the execution layer.
         */

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

            return;
        }

        /*
         * Extract selected strategy.
         */

        RecoveryStrategy strategy =
                decision.getStrategy();

        /*
         * Defensive check.
         *
         * The guard already checks this, but keeping
         * this check here makes the executor defensive.
         */

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

        /*
         * Idempotency check.
         *
         * Prevent duplicate execution of the same
         * recovery strategy for the same recovery case.
         */

        Optional<RecoveryAction> existingAction =
                recoveryActionRepository
                        .findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                                recoveryCase.getId(),
                                strategy
                        );

        if (existingAction.isPresent()) {

            RecoveryAction action =
                    existingAction.get();

            /*
             * Already executed.
             */

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

            /*
             * Already pending.
             */

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

            /*
             * FAILED actions are allowed to create
             * another recovery attempt.
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

        /*
         * Create a new recovery action.
         */

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

        /*
         * Execute the strategy-specific handler.
         */

        try {

            executeAction(
                    recoveryCase,
                    decision
            );

            /*
             * Handler completed successfully.
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

            log.info(
                    "Recovery action executed successfully. " +
                            "actionId={}, recoveryCaseId={}, strategy={}",
                    recoveryAction.getId(),
                    recoveryCase.getId(),
                    strategy
            );

        } catch (Exception exception) {

            /*
             * Handler failed.
             *
             * Persist FAILED state before propagating
             * the exception to the caller.
             */

            recoveryAction.setStatus(
                    RecoveryAction.ActionStatus.FAILED
            );

            recoveryActionRepository.save(
                    recoveryAction
            );

            log.error(
                    "Recovery action execution failed. " +
                            "actionId={}, recoveryCaseId={}, strategy={}",
                    recoveryAction.getId(),
                    recoveryCase.getId(),
                    strategy,
                    exception
            );

            throw exception;
        }
    }

    /**
     * Finds the handler registered for the selected
     * recovery strategy and delegates execution to it.
     */
    private void executeAction(
            RecoveryCase recoveryCase,
            RecoveryDecision decision
    ) {

        RecoveryStrategy strategy =
                decision.getStrategy();

        RecoveryActionHandler handler =
                handlers.get(strategy);

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

        handler.handle(
                recoveryCase,
                decision
        );
    }
}