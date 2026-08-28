package com.reviveai.recovery;

import com.reviveai.entity.RecoveryCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CustomerActionRequiredHandler
        implements RecoveryActionHandler {

    @Override
    public RecoveryStrategy getStrategy() {
        return RecoveryStrategy.CUSTOMER_ACTION_REQUIRED;
    }

    @Override
    public void handle(
            RecoveryCase recoveryCase,
            RecoveryDecision decision
    ) {

        log.info(
                "Customer action required handler invoked. " +
                        "recoveryCaseId={}, score={}, priority={}",
                recoveryCase.getId(),
                decision.getRecoveryScore(),
                decision.getPriority()
        );

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
         */
    }
}