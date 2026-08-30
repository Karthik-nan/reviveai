package com.reviveai.recovery;

import com.reviveai.agent.RecoveryAgentRequest;
import com.reviveai.agent.RecoveryAgentResponse;
import com.reviveai.entity.RecoveryCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RecoveryActionOrchestrator {

    private final RecoveryActionExecutor recoveryActionExecutor;
    private final RecoveryDecisionFactory recoveryDecisionFactory;

    public RecoveryActionOrchestrator(
            RecoveryActionExecutor recoveryActionExecutor,
            RecoveryDecisionFactory recoveryDecisionFactory
    ) {
        this.recoveryActionExecutor =
                recoveryActionExecutor;

        this.recoveryDecisionFactory =
                recoveryDecisionFactory;
    }

    public void execute(
            RecoveryCase recoveryCase,
            RecoveryAgentRequest request,
            RecoveryAgentResponse finalDecision
    ) {

        // =====================================================
        // 1. VALIDATE INPUTS
        // =====================================================

        if (recoveryCase == null) {

            throw new IllegalArgumentException(
                    "Recovery case cannot be null"
            );
        }

        if (request == null) {

            throw new IllegalArgumentException(
                    "Recovery agent request cannot be null"
            );
        }

        if (finalDecision == null) {

            throw new IllegalArgumentException(
                    "Final recovery decision cannot be null"
            );
        }

        if (finalDecision.getRecommendedStrategy() == null) {

            throw new IllegalStateException(
                    "Final recovery decision does not contain a strategy"
            );
        }

        // =====================================================
        // 2. CREATE DOMAIN RECOVERY DECISION
        // =====================================================

        RecoveryDecision decision =
                recoveryDecisionFactory.create(
                        request,
                        finalDecision
                );

        // =====================================================
        // 3. VALIDATE DECISION
        // =====================================================

        if (decision.getStrategy() == null) {

            throw new IllegalStateException(
                    "Recovery decision does not contain a strategy"
            );
        }

        // =====================================================
        // 4. LOG FINAL DECISION
        // =====================================================

        log.info(
                "Executing final recovery decision. " +
                        "recoveryCaseId={}, strategy={}, " +
                        "priority={}, score={}, fallbackUsed={}",
                recoveryCase.getId(),
                decision.getStrategy(),
                decision.getPriority(),
                decision.getRecoveryScore(),
                finalDecision.isFallbackUsed()
        );

        // =====================================================
        // 5. EXECUTE RECOVERY ACTION
        // =====================================================

        recoveryActionExecutor.execute(
                recoveryCase,
                decision
        );

        // =====================================================
        // 6. LOG RESULT
        // =====================================================

        log.info(
                "Recovery action orchestration completed. " +
                        "recoveryCaseId={}, strategy={}, status={}",
                recoveryCase.getId(),
                decision.getStrategy(),
                recoveryCase.getStatus()
        );
    }
}