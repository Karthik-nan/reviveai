package com.reviveai.service;

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
    // Tier 2 ML dependencies
    // =========================================================

    private final RecoveryFeatureMapper recoveryFeatureMapper;
    private final RecoveryPredictionService recoveryPredictionService;


    // =========================================================
    // PROCESS PAYMENT FAILURE
    // =========================================================

    @Transactional
    public void processPaymentFailure(
            PaymentFailedEvent event
    ) {

        // =========================================================
        // 1. VALIDATE EVENT
        // =========================================================

        if (event == null
                || event.getPayload() == null
                || event.getPayload().getPayment() == null
                || event.getPayload().getPayment().getEntity() == null) {

            log.warn(
                    "Ignoring invalid payment failure event"
            );

            return;
        }


        // =========================================================
        // 2. EXTRACT PAYMENT ENTITY
        // =========================================================

        PaymentFailedEvent.Entity payment =
                event.getPayload()
                        .getPayment()
                        .getEntity();


        // =========================================================
        // 3. EXTRACT PAYMENT ID
        // =========================================================

        String paymentId =
                payment.getId();


        // =========================================================
        // 4. EXTRACT SUBSCRIPTION ID
        // =========================================================

        String subscriptionId =
                payment.getSubscriptionId();


        /*
         * Razorpay may provide subscription information through:
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


        // =========================================================
        // 5. VALIDATE PAYMENT ID
        // =========================================================

        if (paymentId == null
                || paymentId.isBlank()) {

            log.warn(
                    "Payment event does not contain payment ID"
            );

            return;
        }


        // =========================================================
        // 6. VALIDATE SUBSCRIPTION ID
        // =========================================================

        if (subscriptionId == null
                || subscriptionId.isBlank()) {

            log.warn(
                    "Payment event does not contain subscription ID. " +
                            "paymentId={}",
                    paymentId
            );

            return;
        }


        // =========================================================
        // 7. IDEMPOTENCY CHECK
        // =========================================================

        if (paymentAttemptRepository
                .findByIdempotencyKey(paymentId)
                .isPresent()) {

            log.info(
                    "Payment already processed. paymentId={}",
                    paymentId
            );

            return;
        }


        // =========================================================
        // 8. FIND SUBSCRIPTION
        // =========================================================

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


        // =========================================================
        // 9. VALIDATE AMOUNT
        // =========================================================

        if (payment.getAmount() == null) {

            log.warn(
                    "Payment event does not contain amount. " +
                            "paymentId={}",
                    paymentId
            );

            return;
        }


        // =========================================================
        // 10. CONVERT RAZORPAY AMOUNT
        // =========================================================

        /*
         * Razorpay sends amounts in the smallest currency unit.
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


        // =========================================================
        // 11. CREATE PAYMENT ATTEMPT
        // =========================================================

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


        // =========================================================
        // 12. MARK SUBSCRIPTION PAST DUE
        // =========================================================

        subscription.setStatus(
                Subscription.SubscriptionStatus.PAST_DUE
        );

        subscriptionRepository.save(
                subscription
        );


        log.info(
                "Subscription marked PAST_DUE. " +
                        "subscriptionId={}, externalSubscriptionId={}",
                subscription.getId(),
                subscriptionId
        );


        // =========================================================
        // 13. EVALUATE SUBSCRIPTION HEALTH
        // =========================================================

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


        // =========================================================
        // 14. DETERMINE RECOVERY POTENTIAL
        // =========================================================

        RecoveryCase.RecoveryPotential recoveryPotential =
                determineRecoveryPotential(
                        amount
                );


        // =========================================================
        // 15. CREATE RECOVERY CASE
        // =========================================================

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


        // =========================================================
        // 16. BUILD TIER 2 ML FEATURES
        // =========================================================

        RecoveryPredictionRequest predictionRequest =
                recoveryFeatureMapper.map(
                        recoveryCase
                );


        log.info(
                "Tier 2 ML features generated. " +
                        "recoveryCaseId={}, paymentAmount={}, " +
                        "retryCount={}, daysPastDue={}, " +
                        "previousSuccessfulPayments={}, " +
                        "previousFailedPayments={}, failureRate={}, " +
                        "recoveryPotential={}, errorCode={}",
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


        // =========================================================
        // 17. RUN TIER 2 ML PREDICTION
        // =========================================================

        RecoveryPredictionResponse prediction =
                recoveryPredictionService.predict(
                        predictionRequest
                );


        if (prediction == null) {

            log.warn(
                    "Tier 2 ML prediction returned null. " +
                            "recoveryCaseId={}, paymentId={}",
                    recoveryCase.getId(),
                    paymentId
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


        if (recoveryScore == null) {

            log.warn(
                    "Tier 2 ML prediction returned null " +
                            "recovery probability. " +
                            "recoveryCaseId={}, paymentId={}",
                    recoveryCase.getId(),
                    paymentId
            );

            recoveryCase.setStatus(
                    RecoveryCase.RecoveryStatus.ESCALATED
            );

            recoveryCaseRepository.save(
                    recoveryCase
            );

            return;
        }


        // =========================================================
        // 18. STORE ML RECOVERY SCORE
        // =========================================================

        recoveryCase.setRecoveryScore(
                recoveryScore
        );

        recoveryCase =
                recoveryCaseRepository.save(
                        recoveryCase
                );


        log.info(
                "Tier 2 ML prediction stored. " +
                        "recoveryCaseId={}, paymentId={}, " +
                        "recoveryProbability={}, modelVersion={}, reason={}",
                recoveryCase.getId(),
                paymentId,
                prediction.getRecoveryProbability(),
                prediction.getModelVersion(),
                prediction.getPredictionReason()
        );


        // =========================================================
        // 19. DETERMINE RECOVERY STRATEGY
        // =========================================================

        RecoveryDecision decision =
                recoveryStrategyEngine.determineStrategy(
                        recoveryCase
                );


        if (decision == null) {

            log.warn(
                    "Recovery strategy engine returned null. " +
                            "recoveryCaseId={}, paymentId={}",
                    recoveryCase.getId(),
                    paymentId
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
                "Recovery strategy determined. " +
                        "recoveryCaseId={}, paymentId={}, " +
                        "strategy={}, priority={}, score={}",
                recoveryCase.getId(),
                paymentId,
                decision.getStrategy(),
                decision.getPriority(),
                decision.getRecoveryScore()
        );


        // =========================================================
        // 20. VALIDATE RECOVERY DECISION
        // =========================================================

        RecoveryDecisionGuard.GuardResult guardResult =
                recoveryDecisionGuard.validate(
                        recoveryCase,
                        decision
                );


        if (!guardResult.isAllowed()) {

            log.warn(
                    "Recovery decision rejected by safety guard. " +
                            "recoveryCaseId={}, paymentId={}, reason={}",
                    recoveryCase.getId(),
                    paymentId,
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
                "Recovery decision approved by safety guard. " +
                        "recoveryCaseId={}, strategy={}, score={}",
                recoveryCase.getId(),
                decision.getStrategy(),
                decision.getRecoveryScore()
        );


        // =========================================================
        // 21. EXECUTE RECOVERY ACTION
        // =========================================================

        recoveryActionExecutor.execute(
                recoveryCase,
                decision
        );


        log.info(
                "Recovery action execution completed. " +
                        "recoveryCaseId={}, strategy={}",
                recoveryCase.getId(),
                decision.getStrategy()
        );
    }


    // =============================================================
    // RECOVERY POTENTIAL
    // =============================================================

    private RecoveryCase.RecoveryPotential
    determineRecoveryPotential(
            BigDecimal amount
    ) {

        if (amount == null) {

            return RecoveryCase.RecoveryPotential.MEDIUM;
        }


        /*
         * <= ₹5,000
         * High recovery potential
         */

        if (amount.compareTo(
                new BigDecimal("5000")
        ) <= 0) {

            return RecoveryCase.RecoveryPotential.HIGH;
        }


        /*
         * > ₹5,000 and <= ₹20,000
         * Medium recovery potential
         */

        if (amount.compareTo(
                new BigDecimal("20000")
        ) <= 0) {

            return RecoveryCase.RecoveryPotential.MEDIUM;
        }


        /*
         * > ₹20,000
         * Low recovery potential
         */

        return RecoveryCase.RecoveryPotential.LOW;
    }
}

