package com.reviveai.recovery;

import com.reviveai.entity.RecoveryCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class ManualReviewHandler
        implements RecoveryActionHandler {

    @Override
    public RecoveryStrategy getStrategy() {

        return RecoveryStrategy.MANUAL_REVIEW;
    }

    @Override
    public RecoveryOutcome handle(
            RecoveryCase recoveryCase,
            RecoveryDecision decision
    ) {

        // =========================================================
        // 1. VALIDATE RECOVERY CASE
        // =========================================================

        if (recoveryCase == null) {

            throw new IllegalArgumentException(
                    "Recovery case cannot be null"
            );
        }

        // =========================================================
        // 2. VALIDATE RECOVERY DECISION
        // =========================================================

        if (decision == null) {

            throw new IllegalArgumentException(
                    "Recovery decision cannot be null"
            );
        }

        // =========================================================
        // 3. VALIDATE STRATEGY
        // =========================================================

        if (decision.getStrategy()
                != RecoveryStrategy.MANUAL_REVIEW) {

            throw new IllegalArgumentException(
                    "Invalid strategy for ManualReviewHandler: "
                            + decision.getStrategy()
            );
        }

        // =========================================================
        // 4. LOG MANUAL REVIEW REQUEST
        // =========================================================

        log.info(
                "Manual review handler invoked. " +
                        "recoveryCaseId={}, strategy={}, score={}, priority={}",
                recoveryCase.getId(),
                decision.getStrategy(),
                decision.getRecoveryScore(),
                decision.getPriority()
        );

        // =========================================================
        // 5. MARK RECOVERY CASE IN PROGRESS
        // =========================================================

        recoveryCase.setStatus(
                RecoveryCase.RecoveryStatus.IN_PROGRESS
        );

        log.info(
                "Recovery case marked IN_PROGRESS for manual review. " +
                        "recoveryCaseId={}",
                recoveryCase.getId()
        );

        // =========================================================
        // 6. SUBMIT MANUAL REVIEW
        // =========================================================

        /*
         * The recovery decision has successfully been routed
         * to the manual-review workflow.
         *
         * The human review itself has not completed yet.
         *
         * Workflow:
         *
         * Recovery Case
         *       ↓
         * Manual review request
         *       ↓
         * Human decision
         *       ↓
         * Recovery action
         *       ↓
         * Payment webhook
         *       ↓
         * RECOVERED / FAILED
         *
         * Therefore this action is SUBMITTED, not FAILED.
         */

        log.info(
                "Recovery case successfully submitted for manual review. " +
                        "recoveryCaseId={}",
                recoveryCase.getId()
        );

        // =========================================================
        // 7. RETURN SUBMITTED
        // =========================================================

        return new RecoveryOutcome(
                RecoveryOutcome.OutcomeStatus.SUBMITTED,
                BigDecimal.ZERO,
                "Recovery case submitted for manual review."
        );
    }
}
