package com.reviveai.recovery;

import com.reviveai.agent.RecoveryAgentRequest;
import com.reviveai.agent.RecoveryAgentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class RecoveryDecisionFactory {

    /**
     * Converts the final policy-approved agent response
     * into the RecoveryDecision consumed by the action layer.
     */
    public RecoveryDecision create(
            RecoveryAgentRequest request,
            RecoveryAgentResponse response
    ) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Recovery agent request cannot be null"
            );
        }

        if (response == null) {
            throw new IllegalArgumentException(
                    "Recovery agent response cannot be null"
            );
        }

        RecoveryStrategy strategy =
                response.getRecommendedStrategy();

        if (strategy == null) {
            throw new IllegalStateException(
                    "Recovery agent response does not contain a strategy"
            );
        }

        BigDecimal recoveryScore =
                request.getRecoveryScore();

        log.info(
                "Creating recovery decision. " +
                        "caseId={}, strategy={}, priority={}, score={}, fallbackUsed={}",
                request.getRecoveryCaseId(),
                strategy,
                response.getPriority(),
                recoveryScore,
                response.isFallbackUsed()
        );

        return RecoveryDecision.builder()
                .strategy(strategy)
                .priority(response.getPriority())
                .recoveryScore(recoveryScore)
                .reason(response.getReason())
                .build();
    }
}