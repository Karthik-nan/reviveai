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
                != RecoveryStrategy.CUSTOMER_ACTION_REQUIRED) {

            throw new IllegalArgumentException(
                    "Invalid strategy for CustomerActionRequiredHandler: "
                            + decision.getStrategy()
            );
        }

        // ---------------------------------------------------------
        // 4. Log handler invocation
        // ---------------------------------------------------------

        log.info(
                "Customer action required handler invoked. " +
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
         * Future implementation:
         *
         * 1. Generate customer recovery action.
         * 2. Send payment-method update notification.
         * 3. Create customer-facing recovery task.
         * 4. Track customer response.
         *
         * For now this handler represents the
         * execution boundary.
         *
         * No payment has been recovered yet.
         */

        log.info(
                "Customer action required. " +
                        "recoveryCaseId={}",
                recoveryCase.getId()
        );

        // ---------------------------------------------------------
        // 6. Return recovery outcome
        // ---------------------------------------------------------

        return new RecoveryOutcome(
                RecoveryOutcome.OutcomeStatus.FAILED,
                BigDecimal.ZERO,
                "Customer action is required before payment can be recovered."
        );
    }
}