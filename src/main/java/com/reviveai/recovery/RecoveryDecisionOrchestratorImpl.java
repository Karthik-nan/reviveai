package com.reviveai.recovery;

import com.reviveai.agent.RecoveryAgentRequest;
import com.reviveai.agent.RecoveryAgentResponse;
import com.reviveai.agent.RecoveryAgentService;
import com.reviveai.policy.PolicyDecisionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RecoveryDecisionOrchestratorImpl
        implements RecoveryDecisionOrchestrator {

    private final RecoveryAgentService recoveryAgentService;

    private final PolicyDecisionService policyDecisionService;

    public RecoveryDecisionOrchestratorImpl(
            RecoveryAgentService recoveryAgentService,
            PolicyDecisionService policyDecisionService
    ) {

        this.recoveryAgentService =
                recoveryAgentService;

        this.policyDecisionService =
                policyDecisionService;
    }

    @Override
    public RecoveryAgentResponse decide(
            RecoveryAgentRequest request
    ) {

        // =====================================================
        // 1. VALIDATE REQUEST
        // =====================================================

        if (request == null) {

            throw new IllegalArgumentException(
                    "Recovery agent request cannot be null"
            );
        }

        log.info(
                "Recovery decision orchestration started. " +
                        "caseId={}",
                request.getRecoveryCaseId()
        );

        // =====================================================
        // 2. AI AGENT RECOMMENDATION
        // =====================================================

        RecoveryAgentResponse agentResponse =
                recoveryAgentService.recommend(
                        request
                );

        if (agentResponse == null) {

            log.warn(
                    "Recovery agent returned null response. " +
                            "caseId={}",
                    request.getRecoveryCaseId()
            );

            throw new IllegalStateException(
                    "Recovery agent returned null response"
            );
        }

        log.info(
                "Recovery agent recommendation received. " +
                        "caseId={}, strategy={}, priority={}, " +
                        "fallbackUsed={}",
                request.getRecoveryCaseId(),
                agentResponse.getRecommendedStrategy(),
                agentResponse.getPriority(),
                agentResponse.isFallbackUsed()
        );

        // =====================================================
        // 3. POLICY VALIDATION
        // =====================================================

        RecoveryAgentResponse finalDecision =
                policyDecisionService.validate(
                        request,
                        agentResponse
                );

        if (finalDecision == null) {

            log.warn(
                    "Policy decision service returned null. " +
                            "caseId={}",
                    request.getRecoveryCaseId()
            );

            throw new IllegalStateException(
                    "Policy decision service returned null"
            );
        }

        log.info(
                "Final policy-approved recovery decision produced. " +
                        "caseId={}, strategy={}, priority={}, " +
                        "reason={}, fallbackUsed={}",
                request.getRecoveryCaseId(),
                finalDecision.getRecommendedStrategy(),
                finalDecision.getPriority(),
                finalDecision.getReason(),
                finalDecision.isFallbackUsed()
        );

        // =====================================================
        // 4. RETURN FINAL DECISION
        // =====================================================

        return finalDecision;
    }
}
