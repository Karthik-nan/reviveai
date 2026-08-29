package com.reviveai.recovery;

import com.reviveai.entity.RecoveryCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class ManualReviewHandler implements RecoveryActionHandler {

    @Override
    public RecoveryStrategy getStrategy() {
        return RecoveryStrategy.MANUAL_REVIEW;
    }

    @Override
    public RecoveryOutcome handle(
            RecoveryCase recoveryCase,
            RecoveryDecision decision
    ) {

        // ---------------------------------------------------------
        // 1. Validate recovery case
        // ---------------------------------------------------------

        if (recoveryCase == null) {
            throw new IllegalArgumentException(
                    "Recovery case cannot be null"
            );
        }

        // ---------------------------------------------------------
        // 2. Validate recovery decision
        // ---------------------------------------------------------

        if (decision == null) {
            throw new IllegalArgumentException(
                    "Recovery decision cannot be null"
            );
        }

        // ---------------------------------------------------------
        // 3. Validate strategy
        // ---------------------------------------------------------

        if (decision.getStrategy()
                != RecoveryStrategy.MANUAL_REVIEW) {

            throw new IllegalArgumentException(
                    "Invalid strategy for ManualReviewHandler: "
                            + decision.getStrategy()
            );
        }

        // ---------------------------------------------------------
        // 4. Log manual review request
        // ---------------------------------------------------------

        log.info(
                "Manual review handler invoked. " +
                        "recoveryCaseId={}, strategy={}, score={}, priority={}",
                recoveryCase.getId(),
                decision.getStrategy(),
                decision.getRecoveryScore(),
                decision.getPriority()
        );

        // ---------------------------------------------------------
        // 5. Execution boundary
        // ---------------------------------------------------------

        /*
         * Manual review workflow will be integrated later.
         *
         * Future implementation:
         *
         * Recovery Case
         *       ↓
         * Manual Review Queue
         *       ↓
         * Human Decision
         *       ↓
         * Recovery Action
         *
         * For now this handler only represents the execution
         * boundary for recovery cases requiring human intervention.
         *
         * No money has been recovered at this point.
         */

        log.info(
                "Recovery case queued for manual review. " +
                        "recoveryCaseId={}",
                recoveryCase.getId()
        );

        // ---------------------------------------------------------
        // 6. Return recovery outcome
        // ---------------------------------------------------------

        return new RecoveryOutcome(
                RecoveryOutcome.OutcomeStatus.FAILED,
                BigDecimal.ZERO,
                "Recovery case requires manual review."
        );
    }
}
