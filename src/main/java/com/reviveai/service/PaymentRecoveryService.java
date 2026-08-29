package com.reviveai.service;

import com.reviveai.agent.RecoveryAgentRequest;
import com.reviveai.agent.RecoveryAgentResponse;
import com.reviveai.agent.RecoveryAgentService;
import com.reviveai.dto.PaymentFailedEvent;
import com.reviveai.entity.PaymentAttempt;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.entity.Subscription;
import com.reviveai.entity.SubscriptionHealth;
import com.reviveai.ml.RecoveryFeatureMapper;
import com.reviveai.ml.RecoveryPredictionRequest;
import com.reviveai.ml.RecoveryPredictionResponse;
import com.reviveai.ml.RecoveryPredictionService;
import com.reviveai.recovery.RecoveryActionExecutor;
import com.reviveai.recovery.RecoveryDecision;
import com.reviveai.recovery.RecoveryDecisionGuard;
import com.reviveai.recovery.RecoveryPriority;
import com.reviveai.recovery.RecoveryStrategy;
import com.reviveai.recovery.RecoveryStrategyEngine;
import com.reviveai.repository.PaymentAttemptRepository;
import com.reviveai.repository.RecoveryCaseRepository;
import com.reviveai.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRecoveryService {

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final RecoveryCaseRepository recoveryCaseRepository;

    private final SubscriptionHealthEvaluator subscriptionHealthEvaluator;

    private final RecoveryStrategyEngine recoveryStrategyEngine;
    private final RecoveryDecisionGuard recoveryDecisionGuard;
    private final RecoveryActionExecutor recoveryActionExecutor;

    // =========================================================
    // TIER 2 ML
    // =========================================================

    private final RecoveryFeatureMapper recoveryFeatureMapper;
    private final RecoveryPredictionService recoveryPredictionService;

    // =========================================================
    // AI RECOVERY AGENT
    // =========================================================

    private final RecoveryAgentService recoveryAgentService;

    // =========================================================
    // PROCESS PAYMENT FAILURE
    // =========================================================

    @Transactional
    public void processPaymentFailure(
            PaymentFailedEvent event
    ) {

        // =====================================================
        // 1. VALIDATE EVENT
        // =====================================================

        if (event == null
                || event.getPayload() == null
                || event.getPayload().getPayment() == null
                || event.getPayload().getPayment().getEntity() == null) {

            log.warn(
                    "Ignoring invalid payment failure event"
            );

            return;
        }

        // =====================================================
        // 2. EXTRACT PAYMENT
        // =====================================================

        PaymentFailedEvent.Entity payment =
                event.getPayload()
                        .getPayment()
                        .getEntity();

        String paymentId =
                payment.getId();

        // =====================================================
        // 3. EXTRACT SUBSCRIPTION ID
        // =====================================================

        String subscriptionId =
                payment.getSubscriptionId();

        /*
         * Razorpay may provide the subscription ID through:
         *
         * payload.payment.entity.subscription_id
         *
         * OR
         *
         * payload.subscription.entity.id
         */

        if ((subscriptionId == null
                || subscriptionId.isBlank())
                && event.getPayload().getSubscription() != null
                && event.getPayload()
                .getSubscription()
                .getEntity() != null) {

            subscriptionId =
                    event.getPayload()
                            .getSubscription()
                            .getEntity()
                            .getId();
        }

        log.info(
                "Processing payment failure. " +
                        "paymentId={}, subscriptionId={}",
                paymentId,
                subscriptionId
        );

        // =====================================================
        // 4. VALIDATE PAYMENT ID
        // =====================================================

        if (paymentId == null
                || paymentId.isBlank()) {

            log.warn(
                    "Payment event does not contain payment ID"
            );

            return;
        }

        // =====================================================
        // 5. VALIDATE SUBSCRIPTION ID
        // =====================================================

        if (subscriptionId == null
                || subscriptionId.isBlank()) {

            log.warn(
                    "Payment event does not contain subscription ID. " +
                            "paymentId={}",
                    paymentId
            );

            return;
        }

        // =====================================================
        // 6. IDEMPOTENCY CHECK
        // =====================================================

        if (paymentAttemptRepository
                .findByIdempotencyKey(paymentId)
                .isPresent()) {

            log.info(
                    "Payment already processed. paymentId={}",
                    paymentId
            );

            return;
        }

        // =====================================================
        // 7. FIND SUBSCRIPTION
        // =====================================================

        Subscription subscription =
                subscriptionRepository
                        .findByExternalSubscriptionId(
                                subscriptionId
                        )
                        .orElse(null);

        if (subscription == null) {

            log.warn(
                    "Subscription not found. " +
                            "externalSubscriptionId={}, paymentId={}",
                    subscriptionId,
                    paymentId
            );

            return;
        }

        log.info(
                "Subscription found. " +
                        "internalId={}, externalSubscriptionId={}",
                subscription.getId(),
                subscriptionId
        );

        // =====================================================
        // 8. VALIDATE AMOUNT
        // =====================================================

        if (payment.getAmount() == null) {

            log.warn(
                    "Payment event does not contain amount. " +
                            "paymentId={}",
                    paymentId
            );

            return;
        }

        // =====================================================
        // 9. CONVERT RAZORPAY AMOUNT
        // =====================================================

        /*
         * Razorpay sends amount in smallest currency unit.
         *
         * Example:
         *
         * 49900 paise = ₹499.00
         */

        BigDecimal amount =
                BigDecimal.valueOf(
                        payment.getAmount()
                ).movePointLeft(2);

        log.info(
                "Payment amount converted. " +
                        "paymentId={}, amount={}",
                paymentId,
                amount
        );

        // =====================================================
        // 10. CREATE PAYMENT ATTEMPT
        // =====================================================

        PaymentAttempt paymentAttempt =
                PaymentAttempt.builder()
                        .subscription(subscription)
                        .externalPaymentId(paymentId)
                        .idempotencyKey(paymentId)
                        .amount(amount)
                        .status(
                                PaymentAttempt.PaymentStatus.FAILED
                        )
                        .gatewayErrorCode(
                                payment.getErrorCode()
                        )
                        .gatewayErrorMessage(
                                payment.getErrorDescription()
                        )
                        .build();

        paymentAttempt =
                paymentAttemptRepository.save(
                        paymentAttempt
                );

        log.info(
                "PaymentAttempt created. " +
                        "id={}, paymentId={}",
                paymentAttempt.getId(),
                paymentId
        );

        // =====================================================
        // 11. MARK SUBSCRIPTION PAST DUE
        // =====================================================

        subscription.setStatus(
                Subscription.SubscriptionStatus.PAST_DUE
        );

        subscriptionRepository.save(
                subscription
        );

        log.info(
                "Subscription marked PAST_DUE. " +
                        "subscriptionId={}",
                subscription.getId()
        );

        // =====================================================
        // 12. EVALUATE SUBSCRIPTION HEALTH
        // =====================================================

        SubscriptionHealth health =
                subscriptionHealthEvaluator.evaluateHealth(
                        subscription
                );

        if (health == null) {

            log.warn(
                    "Subscription health evaluation returned null. " +
                            "subscriptionId={}",
                    subscription.getId()
            );

        } else {

            log.info(
                    "Subscription health evaluated. " +
                            "subscriptionId={}, healthScore={}, " +
                            "riskLevel={}, consecutiveFailures={}, " +
                            "recentFailures={}, behaviorDeclining={}",
                    subscription.getId(),
                    health.getHealthScore(),
                    health.getRiskLevel(),
                    health.getConsecutiveFailures(),
                    health.getRecentFailureCount(),
                    health.getPaymentBehaviorDeclining()
            );
        }

        // =====================================================
        // 13. DETERMINE RECOVERY POTENTIAL
        // =====================================================

        RecoveryCase.RecoveryPotential recoveryPotential =
                determineRecoveryPotential(
                        amount
                );

        // =====================================================
        // 14. CREATE RECOVERY CASE
        // =====================================================

        RecoveryCase recoveryCase =
                RecoveryCase.builder()
                        .subscription(subscription)
                        .failedPayment(paymentAttempt)
                        .status(
                                RecoveryCase.RecoveryStatus.OPEN
                        )
                        .recoveryPotential(
                                recoveryPotential
                        )
                        .amountAtRisk(
                                amount
                        )
                        .amountRecovered(
                                BigDecimal.ZERO
                        )
                        .build();

        recoveryCase =
                recoveryCaseRepository.save(
                        recoveryCase
                );

        log.info(
                "RecoveryCase created. " +
                        "recoveryCaseId={}, paymentId={}, " +
                        "amountAtRisk={}, potential={}",
                recoveryCase.getId(),
                paymentId,
                amount,
                recoveryPotential
        );

        // =====================================================
        // 15. BUILD ML FEATURES
        // =====================================================

        RecoveryPredictionRequest predictionRequest =
                recoveryFeatureMapper.map(
                        recoveryCase
                );

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
        // 16. RUN ML PREDICTION
        // =====================================================

        RecoveryPredictionResponse prediction =
                recoveryPredictionService.predict(
                        predictionRequest
                );

        if (prediction == null
                || prediction.getRecoveryProbability() == null) {

            log.warn(
                    "Tier 2 ML prediction unavailable. " +
                            "Escalating recovery case. " +
                            "recoveryCaseId={}",
                    recoveryCase.getId()
            );

            recoveryCase.setStatus(
                    RecoveryCase.RecoveryStatus.ESCALATED
            );

            recoveryCaseRepository.save(
                    recoveryCase
            );

            return;
        }

        BigDecimal recoveryScore =
                prediction.getRecoveryProbability();

        // =====================================================
        // 17. STORE ML SCORE
        // =====================================================

        recoveryCase.setRecoveryScore(
                recoveryScore
        );

        recoveryCase =
                recoveryCaseRepository.save(
                        recoveryCase
                );

        log.info(
                "Tier 2 ML prediction stored. " +
                        "recoveryCaseId={}, " +
                        "recoveryProbability={}, " +
                        "modelVersion={}, reason={}",
                recoveryCase.getId(),
                prediction.getRecoveryProbability(),
                prediction.getModelVersion(),
                prediction.getPredictionReason()
        );

        // =====================================================
        // 18. DETERMINE RULE-BASED STRATEGY FIRST
        // =====================================================

        RecoveryDecision ruleBasedDecision =
                recoveryStrategyEngine.determineStrategy(
                        recoveryCase
                );

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

        } else {

            log.warn(
                    "Rule-based strategy engine returned null. " +
                            "AI agent will use its own fallback logic. " +
                            "recoveryCaseId={}",
                    recoveryCase.getId()
            );
        }

        // =====================================================
        // 19. BUILD AI AGENT REQUEST
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
                                payment.getErrorCode()
                        )
                        .retryCount(
                                predictionRequest.getRetryCount()
                        )
                        .paymentFailureRate(
                                predictionRequest.getPaymentFailureRate()
                        )
                        .ruleBasedStrategy(
                                ruleBasedStrategy
                        )
                        .ruleBasedPriority(
                                ruleBasedPriority
                        )
                        .build();

        log.info(
                "Sending recovery case to AI agent. " +
                        "recoveryCaseId={}, score={}, " +
                        "recoveryPotential={}, errorCode={}, " +
                        "retryCount={}, failureRate={}, " +
                        "ruleBasedStrategy={}, ruleBasedPriority={}",
                recoveryCase.getId(),
                recoveryScore,
                recoveryCase.getRecoveryPotential(),
                payment.getErrorCode(),
                predictionRequest.getRetryCount(),
                predictionRequest.getPaymentFailureRate(),
                ruleBasedStrategy,
                ruleBasedPriority
        );

        // =====================================================
        // 20. AI AGENT RECOMMENDATION
        // =====================================================

        RecoveryAgentResponse agentResponse;

        try {

            agentResponse =
                    recoveryAgentService.recommend(
                            agentRequest
                    );

        } catch (Exception e) {

            log.error(
                    "Recovery AI agent failed. " +
                            "recoveryCaseId={}",
                    recoveryCase.getId(),
                    e
            );

            recoveryCase.setStatus(
                    RecoveryCase.RecoveryStatus.ESCALATED
            );

            recoveryCaseRepository.save(
                    recoveryCase
            );

            return;
        }

        // =====================================================
        // 21. VALIDATE AGENT RESPONSE
        // =====================================================

        if (agentResponse == null
                || agentResponse.getRecommendedStrategy() == null) {

            log.warn(
                    "AI agent returned invalid recommendation. " +
                            "recoveryCaseId={}",
                    recoveryCase.getId()
            );

            recoveryCase.setStatus(
                    RecoveryCase.RecoveryStatus.ESCALATED
            );

            recoveryCaseRepository.save(
                    recoveryCase
            );

            return;
        }

        log.info(
                "AI agent recommendation received. " +
                        "recoveryCaseId={}, strategy={}, " +
                        "priority={}, reason={}, modelVersion={}, " +
                        "fallbackUsed={}",
                recoveryCase.getId(),
                agentResponse.getRecommendedStrategy(),
                agentResponse.getPriority(),
                agentResponse.getReason(),
                agentResponse.getModelVersion(),
                agentResponse.isFallbackUsed()
        );

        // =====================================================
        // 22. APPLY AGENT RECOMMENDATION
        // =====================================================

        RecoveryStrategy finalStrategy =
                agentResponse.getRecommendedStrategy();

        RecoveryPriority finalPriority =
                agentResponse.getPriority();

        /*
         * If the agent does not return a priority,
         * use HIGH as the safe default.
         */

        if (finalPriority == null) {

            finalPriority =
                    RecoveryPriority.HIGH;
        }

        /*
         * Build the final RecoveryDecision using the
         * recommendation produced by the AI agent.
         *
         * The agent recommends.
         * The executor executes.
         */

        RecoveryDecision finalDecision =
                RecoveryDecision.builder()
                        .strategy(finalStrategy)
                        .priority(finalPriority)
                        .recoveryScore(recoveryScore)
                        .reason(agentResponse.getReason())
                        .build();

        log.info(
                "Final AI-assisted recovery decision created. " +
                        "recoveryCaseId={}, strategy={}, " +
                        "priority={}, score={}",
                recoveryCase.getId(),
                finalDecision.getStrategy(),
                finalDecision.getPriority(),
                finalDecision.getRecoveryScore()
        );

        // =====================================================
        // 23. SAFETY GUARD
        // =====================================================

        RecoveryDecisionGuard.GuardResult guardResult =
                recoveryDecisionGuard.validate(
                        recoveryCase,
                        finalDecision
                );

        if (!guardResult.isAllowed()) {

            log.warn(
                    "Recovery decision rejected by safety guard. " +
                            "recoveryCaseId={}, strategy={}, " +
                            "reason={}",
                    recoveryCase.getId(),
                    finalDecision.getStrategy(),
                    guardResult.getReason()
            );

            recoveryCase.setStatus(
                    RecoveryCase.RecoveryStatus.ESCALATED
            );

            recoveryCaseRepository.save(
                    recoveryCase
            );

            return;
        }

        log.info(
                "AI recovery decision approved by safety guard. " +
                        "recoveryCaseId={}, strategy={}, " +
                        "priority={}, score={}",
                recoveryCase.getId(),
                finalDecision.getStrategy(),
                finalDecision.getPriority(),
                finalDecision.getRecoveryScore()
        );

        // =====================================================
        // 24. EXECUTE RECOVERY ACTION
        // =====================================================

        recoveryActionExecutor.execute(
                recoveryCase,
                finalDecision
        );

        log.info(
                "Recovery action execution completed. " +
                        "recoveryCaseId={}, strategy={}",
                recoveryCase.getId(),
                finalDecision.getStrategy()
        );
    }

    // =========================================================
    // RECOVERY POTENTIAL
    // =========================================================

    private RecoveryCase.RecoveryPotential
    determineRecoveryPotential(
            BigDecimal amount
    ) {

        if (amount == null) {

            return RecoveryCase.RecoveryPotential.MEDIUM;
        }

        /*
         * <= ₹5,000
         * HIGH recovery potential
         */

        if (amount.compareTo(
                new BigDecimal("5000")
        ) <= 0) {

            return RecoveryCase.RecoveryPotential.HIGH;
        }

        /*
         * > ₹5,000 and <= ₹20,000
         * MEDIUM recovery potential
         */

        if (amount.compareTo(
                new BigDecimal("20000")
        ) <= 0) {

            return RecoveryCase.RecoveryPotential.MEDIUM;
        }

        /*
         * > ₹20,000
         * LOW recovery potential
         */

        return RecoveryCase.RecoveryPotential.LOW;
    }
}
