package com.reviveai.recovery;

import com.reviveai.entity.RecoveryCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ManualReviewHandler implements RecoveryActionHandler {

    @Override
    public RecoveryStrategy getStrategy() {
        return RecoveryStrategy.MANUAL_REVIEW;
    }

    @Override
    public void handle(
            RecoveryCase recoveryCase,
            RecoveryDecision decision
    ) {

        log.info(
                "Manual review handler invoked. " +
                        "recoveryCaseId={}, score={}, priority={}",
                recoveryCase.getId(),
                decision.getRecoveryScore(),
                decision.getPriority()
        );

        /*
         * Manual review workflow will be integrated later.
         *
         * For now this handler represents the execution boundary
         * for recovery cases that require human intervention.
         */
    }
}