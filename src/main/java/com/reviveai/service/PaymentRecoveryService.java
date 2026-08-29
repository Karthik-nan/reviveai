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
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRecoveryService {

    private final SubscriptionRepository subscriptionRepository;

    private final PaymentAttemptRepository paymentAttemptRepository;

    private final RecoveryCaseRepository recoveryCaseRepository;

    private final SubscriptionHealthEvaluator subscriptionHealthEvaluator;

    private final RecoveryStrategyEngine recoveryStrategyEngine;

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

        if (event == null) {

            log.warn(
                    "Ignoring null payment failure event"
            );

            return;
        }

        if (event.getPayload() == null) {

            log.warn(
                    "Ignoring payment failure event with null payload"
            );

            return;
        }

        if (event.getPayload().getPayment() == null
                || event.getPayload()
                .getPayment()
                .getEntity() == null) {

            log.warn(
                    "Ignoring payment failure event without payment entity"
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
        // 3. VALIDATE PAYMENT ID
        // =====================================================

        if (paymentId == null
                || paymentId.isBlank()) {

            log.warn(
                    "Payment failure event does not contain payment ID"
            );

            return;
        }

        // =====================================================
        // 4. EXTRACT SUBSCRIPTION ID
        // =====================================================

        String subscriptionId =
                payment.getSubscriptionId();

        /*
         * Preferred:
         *
         * payload.payment.entity.subscription_id
         *
         * Fallback:
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

        // =====================================================
        // 5. VALIDATE SUBSCRIPTION ID
        // =====================================================

        if (subscriptionId == null
                || subscriptionId.isBlank()) {

            log.warn(
                    "Payment failure event does not contain subscription ID. " +
                            "paymentId={}",
                    paymentId
            );

            return;
        }

        log.info(
                "Processing payment failure. " +
                        "paymentId={}, subscriptionId={}",
                paymentId,
                subscriptionId
        );

        // =====================================================
        // 6. IDEMPOTENCY CHECK
        // =====================================================

        if (paymentAttemptRepository
                .findByIdempotencyKey(paymentId)
                .isPresent()) {

            log.info(
                    "Payment failure already processed. " +
                            "Skipping duplicate event. paymentId={}",
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
        // 8. VALIDATE PAYMENT AMOUNT
        // =====================================================

        if (payment.getAmount() == null) {

            log.warn(
                    "Payment failure event does not contain amount. " +
                            "paymentId={}",
                    paymentId
            );

            return;
        }

        if (payment.getAmount() < 0) {

            log.warn(
                    "Payment failure event contains invalid negative amount. " +
                            "paymentId={}, amount={}",
                    paymentId,
                    payment.getAmount()
            );

            return;
        }

        // =====================================================
        // 9. CONVERT RAZORPAY AMOUNT
        // =====================================================

        /*
         * Razorpay amount is represented in the smallest
         * currency unit.
         *
         * Example:
         *
         * 49900 paise = ₹499.00
         */

        BigDecimal amount =
                BigDecimal.valueOf(
                                payment.getAmount()
                        )
                        .movePointLeft(2)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        if (amount.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            log.warn(
                    "Payment amount must be greater than zero. " +
                            "paymentId={}, amount={}",
                    paymentId,
                    amount
            );

            return;
        }

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
                        .externalOrderId(
                                payment.getOrderId()
                        )
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

        try {

            paymentAttempt =
                    paymentAttemptRepository.save(
                            paymentAttempt
                    );

        } catch (Exception exception) {

            log.error(
                    "Failed to persist PaymentAttempt. " +
                            "paymentId={}",
                    paymentId,
                    exception
            );

            throw exception;
        }

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
                determineRecoveryPotential(amount);

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
                        .recoveryScore(
                                BigDecimal.ZERO
                        )
                        .amountAtRisk(amount)
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
        // 16. RUN ML PREDICTION
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
        // 17. VALIDATE ML RESPONSE
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
        // 18. VALIDATE ML SCORE
        // =====================================================

        if (recoveryScore.compareTo(
                BigDecimal.ZERO
        ) < 0
                || recoveryScore.compareTo(
                BigDecimal.ONE
        ) > 0) {

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
        // 19. STORE ML SCORE
        // =====================================================

        /*
         * IMPORTANT:
         *
         * recoveryCase is already a managed JPA entity because
         * this entire method runs inside one transaction.
         *
         * Therefore an additional:
         *
         * recoveryCaseRepository.save(recoveryCase)
         *
         * is unnecessary here.
         *
         * Hibernate dirty checking will persist recoveryScore
         * automatically at transaction commit.
         */

        recoveryCase.setRecoveryScore(
                recoveryScore
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
        // 20. DETERMINE RULE-BASED STRATEGY
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

        } else {

            log.warn(
                    "Rule-based strategy unavailable. " +
                            "AI agent will determine the strategy. " +
                            "recoveryCaseId={}",
                    recoveryCase.getId()
            );
        }

        // =====================================================
        // 21. BUILD AI AGENT REQUEST
        // =====================================================

        RecoveryAgentRequest agentRequest =
                RecoveryAgentRequest.builder()
                        .recoveryCaseId(
                                recoveryCase.getId()
                        )
                        .paymentAmount(amount)
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
        // 22. AI AGENT RECOMMENDATION
        // =====================================================

        RecoveryAgentResponse agentResponse;

        try {

            agentResponse =
                    recoveryAgentService.recommend(
                            agentRequest
                    );

        } catch (Exception exception) {

            log.error(
                    "Recovery AI agent failed. " +
                            "recoveryCaseId={}",
                    recoveryCase.getId(),
                    exception
            );

            escalateRecoveryCase(
                    recoveryCase,
                    "AI recovery agent failed"
            );

            return;
        }

        // =====================================================
        // 23. VALIDATE AGENT RESPONSE
        // =====================================================

        if (agentResponse == null) {

            log.warn(
                    "AI agent returned null response. " +
                            "recoveryCaseId={}",
                    recoveryCase.getId()
            );

            escalateRecoveryCase(
                    recoveryCase,
                    "AI agent returned null"
            );

            return;
        }

        if (agentResponse.getRecommendedStrategy() == null) {

            log.warn(
                    "AI agent returned no strategy. " +
                            "recoveryCaseId={}",
                    recoveryCase.getId()
            );

            escalateRecoveryCase(
                    recoveryCase,
                    "AI agent returned no strategy"
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
        // 24. BUILD FINAL DECISION
        // =====================================================

        RecoveryStrategy finalStrategy =
                agentResponse.getRecommendedStrategy();

        RecoveryPriority finalPriority =
                agentResponse.getPriority();

        if (finalPriority == null) {

            finalPriority =
                    RecoveryPriority.HIGH;
        }

        RecoveryDecision finalDecision =
                RecoveryDecision.builder()
                        .strategy(finalStrategy)
                        .priority(finalPriority)
                        .recoveryScore(
                                recoveryScore
                        )
                        .reason(
                                agentResponse.getReason()
                        )
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
        // 25. MARK CASE IN PROGRESS
        // =====================================================

        recoveryCase.setStatus(
                RecoveryCase.RecoveryStatus.IN_PROGRESS
        );

        /*
         * No explicit save is necessary here because recoveryCase
         * is managed by the current transaction.
         */

        // =====================================================
        // 26. EXECUTE RECOVERY ACTION
        // =====================================================

        try {

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

        } catch (Exception exception) {

            log.error(
                    "Recovery action execution failed. " +
                            "recoveryCaseId={}, strategy={}",
                    recoveryCase.getId(),
                    finalDecision.getStrategy(),
                    exception
            );

            recoveryCase.setStatus(
                    RecoveryCase.RecoveryStatus.FAILED
            );

            /*
             * The entity is already managed, so Hibernate will
             * persist the FAILED state when the transaction commits.
             */

            return;
        }
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

    // =========================================================
    // ESCALATE RECOVERY CASE
    // =========================================================

    private void escalateRecoveryCase(
            RecoveryCase recoveryCase,
            String reason
    ) {

        recoveryCase.setStatus(
                RecoveryCase.RecoveryStatus.ESCALATED
        );

        /*
         * recoveryCase is managed by the active transaction.
         *
         * Explicit save is intentionally not required here.
         */

        log.warn(
                "Recovery case escalated. " +
                        "recoveryCaseId={}, reason={}",
                recoveryCase.getId(),
                reason
        );
    }
}