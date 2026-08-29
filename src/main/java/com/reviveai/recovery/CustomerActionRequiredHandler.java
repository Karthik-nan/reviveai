package com.reviveai.recovery;

import com.reviveai.entity.RecoveryCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class CustomerActionRequiredHandler
        implements RecoveryActionHandler {

    @Override
    public RecoveryStrategy getStrategy() {

        return RecoveryStrategy.CUSTOMER_ACTION_REQUIRED;
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
                != RecoveryStrategy.CUSTOMER_ACTION_REQUIRED) {

            throw new IllegalArgumentException(
                    "Invalid strategy for CustomerActionRequiredHandler: "
                            + decision.getStrategy()
            );
        }

        // =========================================================
        // 4. LOG HANDLER INVOCATION
        // =========================================================

        log.info(
                "Customer action required handler invoked. " +
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
                "Recovery case marked IN_PROGRESS. " +
                        "recoveryCaseId={}",
                recoveryCase.getId()
        );

        // =========================================================
        // 6. CREATE CUSTOMER ACTION
        // =========================================================

        /*
         * This strategy means that the system has determined that
         * customer intervention is required before payment recovery
         * can continue.
         *
         * Example workflow:
         *
         * Recovery Case
         *       ↓
         * Customer action request
         *       ↓
         * Customer responds
         *       ↓
         * Payment method updated / payment retry
         *       ↓
         * Razorpay webhook
         *       ↓
         * RECOVERED
         *
         * The creation/submission of this recovery action succeeded.
         * The payment itself is not recovered yet.
         */

        log.info(
                "Customer action request submitted successfully. " +
                        "recoveryCaseId={}",
                recoveryCase.getId()
        );

        // =========================================================
        // 7. RETURN SUBMITTED
        // =========================================================

        return new RecoveryOutcome(
                RecoveryOutcome.OutcomeStatus.SUBMITTED,
                BigDecimal.ZERO,
                "Customer action request submitted. " +
                        "Waiting for customer action before payment recovery."
        );
    }
}
