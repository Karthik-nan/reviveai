package com.reviveai.service;

import com.reviveai.agent.RecoveryAgentRequest;
import com.reviveai.agent.RecoveryAgentResponse;
import com.reviveai.dto.PaymentFailedEvent;
import com.reviveai.entity.PaymentAttempt;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.entity.Subscription;
import com.reviveai.entity.SubscriptionHealth;
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
import com.reviveai.repository.PaymentAttemptRepository;
import com.reviveai.repository.RecoveryCaseRepository;
import com.reviveai.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRecoveryService {

    private final SubscriptionRepository subscriptionRepository;

    private final PaymentAttemptRepository paymentAttemptRepository;

    private final RecoveryCaseRepository recoveryCaseRepository;

    private final SubscriptionHealthEvaluator subscriptionHealthEvaluator;

    private final RecoveryStrategyEngine recoveryStrategyEngine;

    // =========================================================
    // TIER 2 ML
    // =========================================================

    private final RecoveryFeatureMapper recoveryFeatureMapper;

    private final RecoveryPredictionService recoveryPredictionService;

    // =========================================================
    // RECOVERY DECISION ORCHESTRATION
    // =========================================================

    private final RecoveryDecisionOrchestrator recoveryDecisionOrchestrator;

    // =========================================================
    // RECOVERY ACTION ORCHESTRATION
    // =========================================================

    private final RecoveryActionOrchestrator recoveryActionOrchestrator;


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

        try {

            recoveryCase =
                    recoveryCaseRepository.save(
                            recoveryCase
                    );

            // Link the payment attempt back to the recovery case
            paymentAttempt.setRecoveryCase(recoveryCase);

            paymentAttemptRepository.save(paymentAttempt);

        } catch (Exception exception) {

            log.error(
                    "Failed to create RecoveryCase. " +
                            "paymentId={}",
                    paymentId,
                    exception
            );

            throw exception;
        }

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
                "Recovery decision pipeline starting. " +
                        "recoveryCaseId={}, score={}, " +
                        "ruleBasedStrategy={}, ruleBasedPriority={}",
                recoveryCase.getId(),
                recoveryScore,
                ruleBasedStrategy,
                ruleBasedPriority
        );

        // =====================================================
        // 22. AGENT → POLICY DECISION ORCHESTRATION
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
        // 23. VALIDATE FINAL POLICY DECISION
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
        // 24. MARK CASE IN PROGRESS
        // =====================================================

        recoveryCase.setStatus(
                RecoveryCase.RecoveryStatus.IN_PROGRESS
        );

        recoveryCaseRepository.save(
                recoveryCase
        );

        // =====================================================
        // 25. FINAL DECISION → ACTION ORCHESTRATION
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
    // PROCESS PAYMENT SUCCESS / RECOVERY
    // =========================================================

    /**
     * Processes a successful payment captured after a recovery
     * action was initiated.
     *
     * Flow:
     *
     * payment.captured
     *      ↓
     * find payment/subscription
     *      ↓
     * mark PaymentAttempt SUCCESS
     *      ↓
     * find matching IN_PROGRESS RecoveryCase
     *      ↓
     * mark RecoveryCase RECOVERED
     *      ↓
     * amountRecovered = amountAtRisk
     *      ↓
     * subscription ACTIVE
     *      ↓
     * reevaluate subscription health
     */
    @Transactional
    public void processPaymentSuccess(
            PaymentFailedEvent event
    ) {

        // =====================================================
        // 1. VALIDATE EVENT
        // =====================================================

        if (event == null) {

            log.warn(
                    "Ignoring null payment success event"
            );

            return;
        }

        if (event.getPayload() == null) {

            log.warn(
                    "Ignoring payment success event with null payload"
            );

            return;
        }

        if (event.getPayload().getPayment() == null
                || event.getPayload()
                .getPayment()
                .getEntity() == null) {

            log.warn(
                    "Ignoring payment success event without payment entity"
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

        if (paymentId == null
                || paymentId.isBlank()) {

            log.warn(
                    "Payment success event does not contain payment ID"
            );

            return;
        }

        // =====================================================
        // 3. EXTRACT SUBSCRIPTION ID
        // =====================================================

        String subscriptionId =
                payment.getSubscriptionId();

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

        if (subscriptionId == null
                || subscriptionId.isBlank()) {

            log.warn(
                    "Payment success event does not contain subscription ID. " +
                            "paymentId={}",
                    paymentId
            );

            return;
        }

        log.info(
                "Processing successful payment recovery. " +
                        "paymentId={}, subscriptionId={}",
                paymentId,
                subscriptionId
        );

        // =====================================================
        // 4. FIND SUBSCRIPTION
        // =====================================================

        Subscription subscription =
                subscriptionRepository
                        .findByExternalSubscriptionId(
                                subscriptionId
                        )
                        .orElse(null);

        if (subscription == null) {

            log.warn(
                    "Subscription not found for successful payment. " +
                            "externalSubscriptionId={}, paymentId={}",
                    subscriptionId,
                    paymentId
            );

            return;
        }

        log.info(
                "Subscription found for successful payment. " +
                        "internalId={}, externalSubscriptionId={}",
                subscription.getId(),
                subscriptionId
        );

        // =====================================================
        // 5. CONVERT PAYMENT AMOUNT
        // =====================================================

        if (payment.getAmount() == null
                || payment.getAmount() <= 0) {

            log.warn(
                    "Successful payment contains invalid amount. " +
                            "paymentId={}, amount={}",
                    paymentId,
                    payment.getAmount()
            );

            return;
        }

        BigDecimal amount =
                BigDecimal.valueOf(
                                payment.getAmount()
                        )
                        .movePointLeft(2)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        log.info(
                "Successful payment amount converted. " +
                        "paymentId={}, amount={}",
                paymentId,
                amount
        );

        // =====================================================
        // 6. CHECK IF THIS PAYMENT WAS ALREADY PROCESSED
        // =====================================================

        PaymentAttempt successfulAttempt =
                paymentAttemptRepository
                        .findByIdempotencyKey(paymentId)
                        .orElse(null);

        if (successfulAttempt != null) {

            log.info(
                    "Successful payment already processed. " +
                            "paymentId={}, status={}",
                    paymentId,
                    successfulAttempt.getStatus()
            );

            /*
             * If it is already SUCCESS, the event is a duplicate.
             */
            if (successfulAttempt.getStatus()
                    == PaymentAttempt.PaymentStatus.SUCCESS) {

                return;
            }

            /*
             * If a previous failure attempt somehow used the same
             * payment ID, update it to SUCCESS.
             */
            successfulAttempt.setStatus(
                    PaymentAttempt.PaymentStatus.SUCCESS
            );

            paymentAttemptRepository.save(
                    successfulAttempt
            );

        } else {

            // =================================================
            // 7. CREATE SUCCESSFUL PAYMENT ATTEMPT
            // =================================================

            successfulAttempt =
                    PaymentAttempt.builder()
                            .subscription(subscription)
                            .externalPaymentId(paymentId)
                            .idempotencyKey(paymentId)
                            .externalOrderId(
                                    payment.getOrderId()
                            )
                            .amount(amount)
                            .status(
                                    PaymentAttempt.PaymentStatus.SUCCESS
                            )
                            .build();

            successfulAttempt =
                    paymentAttemptRepository.save(
                            successfulAttempt
                    );

            log.info(
                    "Successful PaymentAttempt created. " +
                            "id={}, paymentId={}",
                    successfulAttempt.getId(),
                    paymentId
            );
        }

        // =====================================================
        // 8. FIND MATCHING RECOVERY CASE
        // =====================================================

        RecoveryCase recoveryCase =
                findMatchingRecoveryCase(
                        subscription,
                        paymentId,
                        payment.getOrderId()
                );

        // =====================================================
        // 9. NO MATCHING RECOVERY CASE
        // =====================================================

        if (recoveryCase == null) {

            log.info(
                    "No matching IN_PROGRESS recovery case found. " +
                            "paymentId={}, subscriptionId={}",
                    paymentId,
                    subscriptionId
            );

            /*
             * This can be a normal successful payment that was
             * not caused by the recovery pipeline.
             *
             * Still restore the subscription.
             */

            subscription.setStatus(
                    Subscription.SubscriptionStatus.ACTIVE
            );

            subscriptionRepository.save(
                    subscription
            );

            SubscriptionHealth health =
                    subscriptionHealthEvaluator.evaluateHealth(
                            subscription
                    );

            if (health != null) {

                log.info(
                        "Subscription health evaluated after normal successful payment. " +
                                "subscriptionId={}, healthScore={}, riskLevel={}",
                        subscription.getId(),
                        health.getHealthScore(),
                        health.getRiskLevel()
                );
            }

            log.info(
                    "Subscription restored to ACTIVE after successful payment. " +
                            "subscriptionId={}",
                    subscription.getId()
            );

            return;
        }

        log.info(
                "Matching recovery case found. " +
                        "recoveryCaseId={}, failedPaymentId={}, " +
                        "successfulPaymentId={}",
                recoveryCase.getId(),
                recoveryCase.getFailedPayment()
                        .getExternalPaymentId(),
                paymentId
        );

        // =====================================================
        // 10. CALCULATE RECOVERED AMOUNT
        // =====================================================

        BigDecimal amountRecovered =
                recoveryCase.getAmountAtRisk();

        if (amountRecovered == null
                || amountRecovered.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            amountRecovered = amount;
        }

        /*
         * Do not record more than the actual captured amount.
         */
        if (amountRecovered.compareTo(amount) > 0) {

            amountRecovered = amount;
        }

        amountRecovered =
                amountRecovered.setScale(
                        2,
                        RoundingMode.HALF_UP
                );

        // =====================================================
        // 11. MARK RECOVERY CASE RECOVERED
        // =====================================================

        recoveryCase.setAmountRecovered(
                amountRecovered
        );

        recoveryCase.setStatus(
                RecoveryCase.RecoveryStatus.RECOVERED
        );

        recoveryCase.setResolvedAt(
                java.time.OffsetDateTime.now()
        );

        recoveryCaseRepository.save(
                recoveryCase
        );

        log.info(
                "Recovery case marked RECOVERED. " +
                        "recoveryCaseId={}, paymentId={}, " +
                        "amountAtRisk={}, amountRecovered={}",
                recoveryCase.getId(),
                paymentId,
                recoveryCase.getAmountAtRisk(),
                amountRecovered
        );

        // =====================================================
        // 12. RESTORE SUBSCRIPTION
        // =====================================================

        subscription.setStatus(
                Subscription.SubscriptionStatus.ACTIVE
        );

        subscriptionRepository.save(
                subscription
        );

        log.info(
                "Subscription restored to ACTIVE after recovery. " +
                        "subscriptionId={}, paymentId={}",
                subscription.getId(),
                paymentId
        );

        // =====================================================
        // 13. RE-EVALUATE SUBSCRIPTION HEALTH
        // =====================================================

        SubscriptionHealth health =
                subscriptionHealthEvaluator.evaluateHealth(
                        subscription
                );

        if (health != null) {

            log.info(
                    "Subscription health re-evaluated after successful recovery. " +
                            "subscriptionId={}, healthScore={}, " +
                            "riskLevel={}, successfulPayments={}, " +
                            "failedPayments={}, consecutiveFailures={}, " +
                            "recentFailures={}, behaviorDeclining={}",
                    subscription.getId(),
                    health.getHealthScore(),
                    health.getRiskLevel(),
                    health.getSuccessfulPaymentCount(),
                    health.getFailedPaymentCount(),
                    health.getConsecutiveFailures(),
                    health.getRecentFailureCount(),
                    health.getPaymentBehaviorDeclining()
            );

        } else {

            log.warn(
                    "Subscription health re-evaluation returned null. " +
                            "subscriptionId={}",
                    subscription.getId()
            );
        }

        log.info(
                "Payment recovery completed successfully. " +
                        "paymentId={}, subscriptionId={}, " +
                        "recoveryCaseId={}, amountRecovered={}",
                paymentId,
                subscriptionId,
                recoveryCase.getId(),
                amountRecovered
        );
    }


    // =========================================================
    // FIND MATCHING RECOVERY CASE
    // =========================================================

    /**
     * Finds the recovery case that belongs to the failed payment
     * associated with the successful recovery payment.
     *
     * Important:
     *
     * We intentionally do NOT blindly select the only IN_PROGRESS
     * recovery case for a subscription.
     *
     * The failed payment relationship must match the payment that
     * initiated the recovery flow.
     */
    private RecoveryCase findMatchingRecoveryCase(
            Subscription subscription,
            String successfulPaymentId,
            String successfulOrderId
    ) {

        if (subscription == null) {

            log.warn(
                    "Cannot match recovery case because subscription is null. " +
                            "successfulPaymentId={}",
                    successfulPaymentId
            );

            return null;
        }

        if (successfulOrderId == null
                || successfulOrderId.isBlank()) {

            log.warn(
                    "Successful payment does not contain order ID. " +
                            "Cannot safely correlate recovery payment. " +
                            "successfulPaymentId={}",
                    successfulPaymentId
            );

            return null;
        }

        /*
         * Find the original failed payment using:
         *
         * subscription + order ID + FAILED status
         */
        PaymentAttempt failedPayment =
                paymentAttemptRepository
                        .findFirstBySubscriptionIdAndExternalOrderIdAndStatus(
                                subscription.getId(),
                                successfulOrderId,
                                PaymentAttempt.PaymentStatus.FAILED
                        )
                        .orElse(null);

        if (failedPayment == null) {

            log.warn(
                    "No failed payment found for recovery order. " +
                            "subscriptionId={}, orderId={}, successfulPaymentId={}",
                    subscription.getId(),
                    successfulOrderId,
                    successfulPaymentId
            );

            return null;
        }

        log.info(
                "Failed payment identified for successful recovery payment. " +
                        "failedPaymentId={}, orderId={}, successfulPaymentId={}",
                failedPayment.getExternalPaymentId(),
                successfulOrderId,
                successfulPaymentId
        );

        /*
         * Find the recovery case belonging to that failed payment.
         */
        RecoveryCase recoveryCase =
                recoveryCaseRepository
                        .findByFailedPaymentId(
                                failedPayment.getId()
                        )
                        .orElse(null);

        if (recoveryCase == null) {

            log.warn(
                    "No recovery case found for failed payment. " +
                            "failedPaymentId={}, successfulPaymentId={}",
                    failedPayment.getId(),
                    successfulPaymentId
            );

            return null;
        }

        /*
         * Only IN_PROGRESS cases can be completed.
         */
        if (recoveryCase.getStatus()
                != RecoveryCase.RecoveryStatus.IN_PROGRESS) {

            log.warn(
                    "Recovery case is not IN_PROGRESS. " +
                            "recoveryCaseId={}, status={}, " +
                            "successfulPaymentId={}",
                    recoveryCase.getId(),
                    recoveryCase.getStatus(),
                    successfulPaymentId
            );

            return null;
        }

        log.info(
                "Recovery case safely matched using order ID. " +
                        "recoveryCaseId={}, failedPaymentId={}, " +
                        "successfulPaymentId={}, orderId={}",
                recoveryCase.getId(),
                failedPayment.getExternalPaymentId(),
                successfulPaymentId,
                successfulOrderId
        );

        return recoveryCase;
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
    }
}
