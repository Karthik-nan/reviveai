package com.reviveai.recovery;

import com.reviveai.entity.RecoveryCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class UpdatePaymentMethodHandler
        implements RecoveryActionHandler {

    @Override
    public RecoveryStrategy getStrategy() {

        return RecoveryStrategy.UPDATE_PAYMENT_METHOD;
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
                != RecoveryStrategy.UPDATE_PAYMENT_METHOD) {

            throw new IllegalArgumentException(
                    "Invalid strategy for UpdatePaymentMethodHandler: "
                            + decision.getStrategy()
            );
        }

        // =========================================================
        // 4. LOG HANDLER INVOCATION
        // =========================================================

        log.info(
                "Payment method update handler invoked. "
                        + "recoveryCaseId={}, strategy={}, score={}, priority={}",
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
                "Recovery case marked IN_PROGRESS for payment method update. "
                        + "recoveryCaseId={}",
                recoveryCase.getId()
        );

        // =========================================================
        // 6. EXECUTION BOUNDARY
        // =========================================================

        /*
         * This handler does not directly change the customer's
         * payment method.
         *
         * Instead, it represents a successfully initiated
         * recovery workflow.
         *
         * Expected flow:
         *
         * Recovery Case
         *       ↓
         * UPDATE_PAYMENT_METHOD
         *       ↓
         * Customer is asked to update payment method
         *       ↓
         * Customer updates payment method
         *       ↓
         * Payment retry
         *       ↓
         * Razorpay webhook
         *       ↓
         * RECOVERED / FAILED
         *
         * Therefore:
         *
         * Action execution = successful
         * Payment recovery = not completed yet
         */

        log.info(
                "Payment method update recovery action initiated. "
                        + "Awaiting customer action. "
                        + "recoveryCaseId={}",
                recoveryCase.getId()
        );

        // =========================================================
        // 7. RETURN SUBMITTED
        // =========================================================

        /*
         * IMPORTANT:
         *
         * Do NOT return FAILED here.
         *
         * FAILED means the recovery action itself failed.
         *
         * SUBMITTED means:
         *
         * "The recovery action was successfully initiated,
         * but the actual payment recovery is still pending."
         *
         * RecoveryActionExecutor will therefore mark:
         *
         * RecoveryAction -> EXECUTED
         * RecoveryCase   -> IN_PROGRESS
         *
         * This is exactly what we want.
         */

        return new RecoveryOutcome(
                RecoveryOutcome.OutcomeStatus.SUBMITTED,
                BigDecimal.ZERO,
                "Payment method update request initiated. "
                        + "Awaiting customer action."
        );
    }
}
