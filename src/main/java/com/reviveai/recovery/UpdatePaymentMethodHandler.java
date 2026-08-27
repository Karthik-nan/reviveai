package com.reviveai.recovery;

import com.reviveai.entity.RecoveryCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UpdatePaymentMethodHandler
        implements RecoveryActionHandler {

    @Override
    public RecoveryStrategy getStrategy() {
        return RecoveryStrategy.UPDATE_PAYMENT_METHOD;
    }

    @Override
    public void handle(
            RecoveryCase recoveryCase,
            RecoveryDecision decision
    ) {

        log.info(
                "Payment method update handler invoked. " +
                        "recoveryCaseId={}, score={}, priority={}",
                recoveryCase.getId(),
                decision.getRecoveryScore(),
                decision.getPriority()
        );

        // Actual payment-method update flow will be implemented later.
    }
}