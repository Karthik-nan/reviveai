package com.reviveai.policy;

import com.reviveai.agent.RecoveryAgentRequest;
import com.reviveai.agent.RecoveryAgentResponse;
import com.reviveai.recovery.RecoveryPriority;
import com.reviveai.recovery.RecoveryStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Deterministic policy layer between the AI recovery agent
 * and the recovery action layer.
 *
 * The AI agent may recommend a strategy, but it does not
 * have authority to bypass deterministic business rules.
 */
@Slf4j
@Service
public class PolicyDecisionServiceImpl
        implements PolicyDecisionService {

    private static final BigDecimal ZERO =
            BigDecimal.ZERO;

    private static final BigDecimal ONE =
            BigDecimal.ONE;

    /*
     * Minimum recovery probability required for
     * automated recovery actions.
     *
     * Below this threshold, the case is sent
     * to manual review.
     */
    private static final BigDecimal AUTOMATION_THRESHOLD =
            new BigDecimal("0.40");

    @Override
    public RecoveryAgentResponse validate(
            RecoveryAgentRequest request,
            RecoveryAgentResponse agentResponse
    ) {

        // =====================================================
        // 1. VALIDATE REQUEST
        // =====================================================

        if (request == null) {

            throw new IllegalArgumentException(
                    "Recovery agent request cannot be null"
            );
        }

        // =====================================================
        // 2. VALIDATE AGENT RESPONSE
        // =====================================================

        if (agentResponse == null) {

            throw new IllegalArgumentException(
                    "Recovery agent response cannot be null"
            );
        }

        log.info(
                "Policy validation started. " +
                        "caseId={}, agentStrategy={}, agentPriority={}, " +
                        "score={}, ruleStrategy={}, rulePriority={}",
                request.getRecoveryCaseId(),
                agentResponse.getRecommendedStrategy(),
                agentResponse.getPriority(),
                request.getRecoveryScore(),
                request.getRuleBasedStrategy(),
                request.getRuleBasedPriority()
        );

        // =====================================================
        // 3. VALIDATE RECOVERY SCORE
        // =====================================================

        BigDecimal recoveryScore =
                request.getRecoveryScore();

        if (recoveryScore == null) {

            log.warn(
                    "Recovery score is missing. " +
                            "Sending case to manual review. caseId={}",
                    request.getRecoveryCaseId()
            );

            return manualReview(
                    "Recovery score is missing. " +
                            "Automated recovery is not permitted."
            );
        }

        if (recoveryScore.compareTo(ZERO) < 0
                || recoveryScore.compareTo(ONE) > 0) {

            log.warn(
                    "Recovery score is outside valid range. " +
                            "caseId={}, score={}",
                    request.getRecoveryCaseId(),
                    recoveryScore
            );

            return manualReview(
                    "Recovery score is outside the valid range 0.00-1.00."
            );
        }

        // =====================================================
        // 4. VALIDATE AGENT STRATEGY
        // =====================================================

        RecoveryStrategy agentStrategy =
                agentResponse.getRecommendedStrategy();

        if (agentStrategy == null) {

            log.warn(
                    "AI agent returned no strategy. " +
                            "caseId={}",
                    request.getRecoveryCaseId()
            );

            return fallbackToRule(
                    request,
                    "AI agent returned no strategy."
            );
        }

        // =====================================================
        // 5. VALIDATE AGENT PRIORITY
        // =====================================================

        RecoveryPriority agentPriority =
                agentResponse.getPriority();

        if (agentPriority == null) {

            log.warn(
                    "AI agent returned no priority. " +
                            "caseId={}",
                    request.getRecoveryCaseId()
            );

            return fallbackToRule(
                    request,
                    "AI agent returned no priority."
            );
        }

        // =====================================================
        // 6. LOW RECOVERY SCORE
        // =====================================================

        if (recoveryScore.compareTo(
                AUTOMATION_THRESHOLD
        ) < 0) {

            log.info(
                    "Recovery score below automation threshold. " +
                            "caseId={}, score={}, threshold={}",
                    request.getRecoveryCaseId(),
                    recoveryScore,
                    AUTOMATION_THRESHOLD
            );

            return manualReview(
                    "Recovery score is below the automated recovery threshold. " +
                            "Manual review is required."
            );
        }

        // =====================================================
        // 7. GET DETERMINISTIC RULE DECISION
        // =====================================================

        RecoveryStrategy ruleStrategy =
                request.getRuleBasedStrategy();

        RecoveryPriority rulePriority =
                request.getRuleBasedPriority();

        // =====================================================
        // 8. NO RULE DECISION
        // =====================================================

        if (ruleStrategy == null) {

            /*
             * There is no deterministic recommendation.
             *
             * The AI recommendation can be accepted provided
             * it passed all previous validation checks.
             */

            log.info(
                    "No rule-based strategy available. " +
                            "Accepting validated AI recommendation. " +
                            "caseId={}, strategy={}, priority={}",
                    request.getRecoveryCaseId(),
                    agentStrategy,
                    agentPriority
            );

            return approved(
                    agentStrategy,
                    agentPriority,
                    recoveryScore,
                    agentResponse.getReason(),
                    agentResponse.getModelVersion()
            );
        }

        // =====================================================
        // 9. RULE-BASED MANUAL REVIEW
        // =====================================================

        if (ruleStrategy
                == RecoveryStrategy.MANUAL_REVIEW) {

            /*
             * Deterministic rules explicitly require manual
             * review.
             *
             * AI cannot override this.
             */

            log.warn(
                    "Rule engine requires manual review. " +
                            "AI recommendation will not override policy. " +
                            "caseId={}, aiStrategy={}, ruleStrategy={}",
                    request.getRecoveryCaseId(),
                    agentStrategy,
                    ruleStrategy
            );

            return manualReview(
                    "Deterministic recovery rules require manual review. " +
                            "AI recommendation cannot override this policy."
            );
        }

        // =====================================================
        // 10. AI STRATEGY MUST MATCH RULE STRATEGY
        // =====================================================

        if (agentStrategy != ruleStrategy) {

            log.warn(
                    "AI recommendation conflicts with deterministic rule. " +
                            "caseId={}, aiStrategy={}, ruleStrategy={}",
                    request.getRecoveryCaseId(),
                    agentStrategy,
                    ruleStrategy
            );

            /*
             * The deterministic rule wins.
             *
             * This is the most important policy boundary in
             * the recovery decision pipeline.
             */

            return fallbackToRule(
                    request,
                    "AI recommendation conflicted with deterministic " +
                            "recovery policy. Rule-based strategy was selected."
            );
        }

        // =====================================================
        // 11. VALIDATE PRIORITY
        // =====================================================

        RecoveryPriority finalPriority;

        if (rulePriority != null) {

            /*
             * The deterministic priority is authoritative.
             *
             * The AI cannot increase or decrease the business
             * priority independently.
             */

            finalPriority =
                    rulePriority;

        } else {

            finalPriority =
                    agentPriority;
        }

        // =====================================================
        // 12. APPROVE DECISION
        // =====================================================

        log.info(
                "Recovery decision approved by policy. " +
                        "caseId={}, strategy={}, priority={}, score={}",
                request.getRecoveryCaseId(),
                ruleStrategy,
                finalPriority,
                recoveryScore
        );

        return approved(
                ruleStrategy,
                finalPriority,
                recoveryScore,
                agentResponse.getReason(),
                agentResponse.getModelVersion()
        );
    }

    // =========================================================
    // FALLBACK TO RULE ENGINE
    // =========================================================

    private RecoveryAgentResponse fallbackToRule(
            RecoveryAgentRequest request,
            String policyReason
    ) {

        RecoveryStrategy ruleStrategy =
                request.getRuleBasedStrategy();

        RecoveryPriority rulePriority =
                request.getRuleBasedPriority();

        // =====================================================
        // RULE ENGINE ALSO UNAVAILABLE
        // =====================================================

        if (ruleStrategy == null) {

            log.warn(
                    "No deterministic fallback available. " +
                            "Sending case to manual review. caseId={}",
                    request.getRecoveryCaseId()
            );

            return manualReview(
                    policyReason +
                            " No deterministic fallback is available."
            );
        }

        // =====================================================
        // RULE ENGINE REQUIRES MANUAL REVIEW
        // =====================================================

        if (ruleStrategy
                == RecoveryStrategy.MANUAL_REVIEW) {

            return manualReview(
                    policyReason +
                            " Deterministic policy requires manual review."
            );
        }

        // =====================================================
        // RULE ENGINE FALLBACK
        // =====================================================

        RecoveryPriority priority =
                rulePriority != null
                        ? rulePriority
                        : RecoveryPriority.LOW;

        log.info(
                "Using deterministic rule-based fallback. " +
                        "caseId={}, strategy={}, priority={}",
                request.getRecoveryCaseId(),
                ruleStrategy,
                priority
        );

        return RecoveryAgentResponse.builder()
                .recommendedStrategy(ruleStrategy)
                .priority(priority)
                .reason(policyReason)
                .modelVersion("POLICY_RULE_FALLBACK")
                .fallbackUsed(true)
                .build();
    }

    // =========================================================
    // APPROVED DECISION
    // =========================================================

    private RecoveryAgentResponse approved(
            RecoveryStrategy strategy,
            RecoveryPriority priority,
            BigDecimal recoveryScore,
            String agentReason,
            String modelVersion
    ) {

        String reason =
                agentReason == null
                        || agentReason.isBlank()
                        ? "Recovery recommendation approved by policy."
                        : agentReason;

        return RecoveryAgentResponse.builder()
                .recommendedStrategy(strategy)
                .priority(priority)
                .reason(reason)
                .modelVersion(
                        modelVersion == null
                                ? "UNKNOWN"
                                : modelVersion
                )
                .fallbackUsed(false)
                .build();
    }

    // =========================================================
    // MANUAL REVIEW
    // =========================================================

    private RecoveryAgentResponse manualReview(
            String reason
    ) {

        return RecoveryAgentResponse.builder()
                .recommendedStrategy(
                        RecoveryStrategy.MANUAL_REVIEW
                )
                .priority(
                        RecoveryPriority.HIGH
                )
                .reason(reason)
                .modelVersion(
                        "POLICY_MANUAL_REVIEW"
                )
                .fallbackUsed(true)
                .build();
    }
}