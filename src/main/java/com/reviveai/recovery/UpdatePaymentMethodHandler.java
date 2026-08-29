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
                != RecoveryStrategy.UPDATE_PAYMENT_METHOD) {

            throw new IllegalArgumentException(
                    "Invalid strategy for UpdatePaymentMethodHandler: "
                            + decision.getStrategy()
            );
        }

        // ---------------------------------------------------------
        // 4. Log handler invocation
        // ---------------------------------------------------------

        log.info(
                "Payment method update handler invoked. " +
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
         * Actual payment-method update flow will be implemented later.
         *
         * Future flow:
         *
         * Recovery Case
         *       ↓
         * Customer notification
         *       ↓
         * Customer updates payment method
         *       ↓
         * Payment retry
         *       ↓
         * Payment webhook
         *       ↓
         * RECOVERED / FAILED
         *
         * No payment has been recovered at this point.
         */

        log.info(
                "Payment method update execution boundary reached. " +
                        "recoveryCaseId={}",
                recoveryCase.getId()
        );

        // ---------------------------------------------------------
        // 6. Return recovery outcome
        // ---------------------------------------------------------

        return new RecoveryOutcome(
                RecoveryOutcome.OutcomeStatus.FAILED,
                BigDecimal.ZERO,
                "Customer payment method update is required."
        );
    }
}