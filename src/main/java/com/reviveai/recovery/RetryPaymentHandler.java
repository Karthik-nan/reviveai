package com.reviveai.recovery;

import com.reviveai.entity.RecoveryCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RetryPaymentHandler implements RecoveryActionHandler {

    @Override
    public RecoveryStrategy getStrategy() {
        return RecoveryStrategy.RETRY_PAYMENT;
    }

    @Override
    public void handle(
            RecoveryCase recoveryCase,
            RecoveryDecision decision
    ) {

        log.info(
                "Retry payment handler invoked. " +
                        "recoveryCaseId={}, score={}, priority={}",
                recoveryCase.getId(),
                decision.getRecoveryScore(),
                decision.getPriority()
        );

        /*
         * Actual Razorpay payment retry will be integrated later.
         *
         * For now this handler represents the execution boundary.
         */
    }
}