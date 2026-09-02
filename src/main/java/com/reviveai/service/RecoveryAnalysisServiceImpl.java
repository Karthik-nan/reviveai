package com.reviveai.service;

import com.reviveai.agent.RecoveryAgentRequest;
import com.reviveai.agent.RecoveryAgentResponse;
import com.reviveai.entity.PaymentAttempt;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.ml.RecoveryFeatureMapper;
import com.reviveai.ml.RecoveryPredictionRequest;
import com.reviveai.ml.RecoveryPredictionResponse;
import com.reviveai.ml.RecoveryPredictionService;
import com.reviveai.recovery.RecoveryActionOrchestrator;
import com.reviveai.recovery.RecoveryDecision;
import com.reviveai.recovery.RecoveryDecisionOrchestrator;
import com.reviveai.recovery.RecoveryPriority;
import com.reviveai.recovery.RecoveryStrategy;
import com.reviveai.recovery.RecoveryStrategyEngine;
import com.reviveai.repository.RecoveryCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecoveryAnalysisServiceImpl
        implements RecoveryAnalysisService {

    private final RecoveryFeatureMapper recoveryFeatureMapper;

    private final RecoveryPredictionService recoveryPredictionService;

    private final RecoveryStrategyEngine recoveryStrategyEngine;

    private final RecoveryDecisionOrchestrator recoveryDecisionOrchestrator;

    private final RecoveryActionOrchestrator recoveryActionOrchestrator;

    private final RecoveryCaseRepository recoveryCaseRepository;

    private final AuditEventService auditEventService;


    // =========================================================
    // ANALYZE RECOVERY CASE
    // =========================================================

    @Override
    @Transactional
    public void analyzeRecoveryCase(
            RecoveryCase recoveryCase
    ) {

        if (recoveryCase == null) {

            throw new IllegalArgumentException(
                    "Recovery case cannot be null"
            );
        }

        if (recoveryCase.getFailedPayment() == null) {

            throw new IllegalArgumentException(
                    "Recovery case failed payment cannot be null"
            );
        }

        BigDecimal amount =
                recoveryCase.getAmountAtRisk();

        if (amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Recovery case amount at risk must be greater than zero"
            );
        }

        PaymentAttempt failedPayment =
                recoveryCase.getFailedPayment();

        String paymentErrorCode =
                failedPayment.getGatewayErrorCode();

        log.info(
                "Starting recovery analysis. " +
                        "recoveryCaseId={}, amountAtRisk={}, " +
                        "errorCode={}, currentStatus={}",
                recoveryCase.getId(),
                amount,
                paymentErrorCode,
                recoveryCase.getStatus()
        );


        // =====================================================
        // 1. BUILD ML FEATURES
        // =====================================================

        RecoveryPredictionRequest predictionRequest;

        try {

            predictionRequest =
                    recoveryFeatureMapper.map(
                            recoveryCase
                    );

        } catch (Exception exception) {

            log.error(
                    "Failed to generate ML features. " +
                            "recoveryCaseId={}",
                    recoveryCase.getId(),
                    exception
            );

            escalateRecoveryCase(
                    recoveryCase,
                    "ML feature generation failed"
            );

            return;
        }

        if (predictionRequest == null) {

            log.warn(
                    "ML feature mapper returned null. " +
                            "recoveryCaseId={}",
                    recoveryCase.getId()
            );

            escalateRecoveryCase(
                    recoveryCase,
                    "ML feature generation returned null"
            );

            return;
        }

        log.info(
                "Tier 2 ML features generated. " +
                        "recoveryCaseId={}, paymentAmount={}, " +
                        "retryCount={}, daysPastDue={}, " +
                        "previousSuccessfulPayments={}, " +
                        "previousFailedPayments={}, " +
                        "failureRate={}, recoveryPotential={}, " +
                        "errorCode={}",
                recoveryCase.getId(),
                predictionRequest.getPaymentAmount(),
                predictionRequest.getRetryCount(),
                predictionRequest.getDaysPastDue(),
                predictionRequest.getPreviousSuccessfulPayments(),
                predictionRequest.getPreviousFailedPayments(),
                predictionRequest.getPaymentFailureRate(),
                predictionRequest.getRecoveryPotential(),
                predictionRequest.getErrorCode()
        );


        // =====================================================
        // 2. RUN ML PREDICTION
        // =====================================================

        RecoveryPredictionResponse prediction;

        try {

            prediction =
                    recoveryPredictionService.predict(
                            predictionRequest
                    );

        } catch (Exception exception) {

            log.error(
                    "Tier 2 ML prediction failed. " +
                            "recoveryCaseId={}",
                    recoveryCase.getId(),
                    exception
            );

            escalateRecoveryCase(
                    recoveryCase,
                    "ML prediction failed"
            );

            return;
        }


        // =====================================================
        // 3. VALIDATE ML RESPONSE
        // =====================================================

        if (prediction == null
                || prediction.getRecoveryProbability() == null) {

            log.warn(
                    "Tier 2 ML prediction unavailable. " +
                            "Escalating recovery case. " +
                            "recoveryCaseId={}",
                    recoveryCase.getId()
            );

            escalateRecoveryCase(
                    recoveryCase,
                    "ML prediction unavailable"
            );

            return;
        }

        BigDecimal recoveryScore =
                prediction.getRecoveryProbability();


        // =====================================================
        // 4. VALIDATE ML SCORE
        // =====================================================

        if (recoveryScore.compareTo(BigDecimal.ZERO) < 0
                || recoveryScore.compareTo(BigDecimal.ONE) > 0) {

            log.warn(
                    "Invalid ML recovery probability. " +
                            "recoveryCaseId={}, score={}",
                    recoveryCase.getId(),
                    recoveryScore
            );

            escalateRecoveryCase(
                    recoveryCase,
                    "ML returned probability outside 0-1"
            );

            return;
        }

        recoveryScore =
                recoveryScore.setScale(
                        2,
                        RoundingMode.HALF_UP
                );


        // =====================================================
        // 5. STORE ML SCORE
        // =====================================================

        recoveryCase.setRecoveryScore(
                recoveryScore
        );

        recoveryCaseRepository.save(
                recoveryCase
        );

        log.info(
                "Tier 2 ML prediction stored. " +
                        "recoveryCaseId={}, " +
                        "recoveryProbability={}, " +
                        "modelVersion={}, reason={}",
                recoveryCase.getId(),
                recoveryScore,
                prediction.getModelVersion(),
                prediction.getPredictionReason()
        );


        // =====================================================
        // AUDIT: ML PREDICTION
        // =====================================================

        auditEventService.record(
                "ML_PREDICTION_GENERATED",
                "RECOVERY_CASE",
                recoveryCase.getId(),
                "ML_MODEL",
                String.format(
                        "{\"recoveryProbability\":%s,\"modelVersion\":\"%s\",\"reason\":\"%s\"}",
                        recoveryScore,
                        prediction.getModelVersion(),
                        prediction.getPredictionReason()
                )
        );


        // =====================================================
        // 6. DETERMINE RULE-BASED STRATEGY
        // =====================================================

        RecoveryDecision ruleBasedDecision = null;

        try {

            ruleBasedDecision =
                    recoveryStrategyEngine.determineStrategy(
                            recoveryCase
                    );

        } catch (Exception exception) {

            log.error(
                    "Rule-based strategy engine failed. " +
                            "recoveryCaseId={}",
                    recoveryCase.getId(),
                    exception
            );
        }

        RecoveryStrategy ruleBasedStrategy = null;

        RecoveryPriority ruleBasedPriority = null;

        if (ruleBasedDecision != null) {

            ruleBasedStrategy =
                    ruleBasedDecision.getStrategy();

            ruleBasedPriority =
                    ruleBasedDecision.getPriority();

            log.info(
                    "Rule-based recovery decision generated. " +
                            "recoveryCaseId={}, strategy={}, priority={}",
                    recoveryCase.getId(),
                    ruleBasedStrategy,
                    ruleBasedPriority
            );

            auditEventService.record(
                    "RULE_DECISION_GENERATED",
                    "RECOVERY_CASE",
                    recoveryCase.getId(),
                    "RULE_ENGINE",
                    String.format(
                            "{\"strategy\":\"%s\",\"priority\":\"%s\"}",
                            ruleBasedStrategy,
                            ruleBasedPriority
                    )
            );

        } else {

            log.warn(
                    "Rule-based strategy unavailable. " +
                            "AI agent will determine the strategy. " +
                            "recoveryCaseId={}",
                    recoveryCase.getId()
            );
        }


        // =====================================================
        // 7. BUILD AI AGENT REQUEST
        // =====================================================

        RecoveryAgentRequest agentRequest =
                RecoveryAgentRequest.builder()
                        .recoveryCaseId(
                                recoveryCase.getId()
                        )
                        .paymentAmount(
                                amount
                        )
                        .recoveryScore(
                                recoveryScore
                        )
                        .recoveryPotential(
                                recoveryCase.getRecoveryPotential()
                        )
                        .paymentErrorCode(
                                paymentErrorCode
                        )
                        .retryCount(
                                predictionRequest.getRetryCount()
                        )
                        .paymentFailureRate(
                                predictionRequest
                                        .getPaymentFailureRate()
                        )
                        .ruleBasedStrategy(
                                ruleBasedStrategy
                        )
                        .ruleBasedPriority(
                                ruleBasedPriority
                        )
                        .build();

        log.info(
                "Recovery decision pipeline starting. " +
                        "recoveryCaseId={}, score={}, " +
                        "ruleBasedStrategy={}, ruleBasedPriority={}",
                recoveryCase.getId(),
                recoveryScore,
                ruleBasedStrategy,
                ruleBasedPriority
        );


        // =====================================================
        // 8. AGENT → POLICY DECISION ORCHESTRATION
        // =====================================================

        RecoveryAgentResponse finalDecision;

        try {

            finalDecision =
                    recoveryDecisionOrchestrator.decide(
                            agentRequest
                    );

        } catch (Exception exception) {

            log.error(
                    "Recovery decision orchestration failed. " +
                            "recoveryCaseId={}",
                    recoveryCase.getId(),
                    exception
            );

            escalateRecoveryCase(
                    recoveryCase,
                    "Recovery decision orchestration failed"
            );

            return;
        }


        // =====================================================
        // 9. VALIDATE FINAL POLICY DECISION
        // =====================================================

        if (finalDecision == null) {

            log.warn(
                    "Recovery decision orchestrator returned null. " +
                            "recoveryCaseId={}",
                    recoveryCase.getId()
            );

            escalateRecoveryCase(
                    recoveryCase,
                    "Final recovery decision was null"
            );

            return;
        }

        if (finalDecision.getRecommendedStrategy() == null) {

            log.warn(
                    "Final recovery decision contains no strategy. " +
                            "recoveryCaseId={}",
                    recoveryCase.getId()
            );

            escalateRecoveryCase(
                    recoveryCase,
                    "Final recovery decision contains no strategy"
            );

            return;
        }

        log.info(
                "Policy-approved recovery decision received. " +
                        "recoveryCaseId={}, strategy={}, " +
                        "priority={}, fallbackUsed={}, reason={}",
                recoveryCase.getId(),
                finalDecision.getRecommendedStrategy(),
                finalDecision.getPriority(),
                finalDecision.isFallbackUsed(),
                finalDecision.getReason()
        );


        // =====================================================
        // AUDIT: POLICY DECISION
        // =====================================================

        auditEventService.record(
                "POLICY_DECISION",
                "RECOVERY_CASE",
                recoveryCase.getId(),
                "POLICY_ENGINE",
                String.format(
                        "{\"strategy\":\"%s\",\"priority\":\"%s\",\"fallbackUsed\":%s,\"reason\":\"%s\"}",
                        finalDecision.getRecommendedStrategy(),
                        finalDecision.getPriority(),
                        finalDecision.isFallbackUsed(),
                        finalDecision.getReason()
                )
        );


        // =====================================================
        // 10. MARK CASE IN PROGRESS
        // =====================================================

        recoveryCase.setStatus(
                RecoveryCase.RecoveryStatus.IN_PROGRESS
        );

        recoveryCaseRepository.save(
                recoveryCase
        );


        // =====================================================
        // 11. FINAL DECISION → ACTION ORCHESTRATION
        // =====================================================

        try {

            recoveryActionOrchestrator.execute(
                    recoveryCase,
                    agentRequest,
                    finalDecision
            );

            log.info(
                    "Recovery action pipeline completed. " +
                            "recoveryCaseId={}, strategy={}, status={}",
                    recoveryCase.getId(),
                    finalDecision.getRecommendedStrategy(),
                    recoveryCase.getStatus()
            );

            auditEventService.record(
                    "RECOVERY_ACTION_EXECUTED",
                    "RECOVERY_CASE",
                    recoveryCase.getId(),
                    "SYSTEM",
                    String.format(
                            "{\"strategy\":\"%s\",\"status\":\"%s\"}",
                            finalDecision.getRecommendedStrategy(),
                            recoveryCase.getStatus()
                    )
            );

        } catch (Exception exception) {

            log.error(
                    "Recovery action orchestration failed. " +
                            "recoveryCaseId={}, strategy={}",
                    recoveryCase.getId(),
                    finalDecision.getRecommendedStrategy(),
                    exception
            );

            recoveryCase.setStatus(
                    RecoveryCase.RecoveryStatus.FAILED
            );

            recoveryCaseRepository.save(
                    recoveryCase
            );

            throw new RuntimeException(
                    "Recovery action orchestration failed",
                    exception
            );
        }
    }


    // =========================================================
    // ESCALATE RECOVERY CASE
    // =========================================================

    private void escalateRecoveryCase(
            RecoveryCase recoveryCase,
            String reason
    ) {

        if (recoveryCase == null) {

            log.warn(
                    "Unable to escalate null recovery case. " +
                            "reason={}",
                    reason
            );

            return;
        }

        recoveryCase.setStatus(
                RecoveryCase.RecoveryStatus.ESCALATED
        );

        recoveryCase.setResolvedAt(
                java.time.OffsetDateTime.now()
        );

        recoveryCaseRepository.save(
                recoveryCase
        );

        log.warn(
                "Recovery case escalated. " +
                        "recoveryCaseId={}, reason={}",
                recoveryCase.getId(),
                reason
        );

        auditEventService.record(
                "RECOVERY_ESCALATED",
                "RECOVERY_CASE",
                recoveryCase.getId(),
                "SYSTEM",
                String.format(
                        "{\"reason\":\"%s\"}",
                        reason
                )
        );
    }
}
