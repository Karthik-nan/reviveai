package com.reviveai.service;

import com.reviveai.dto.PaymentFailedEvent;
import com.reviveai.entity.PaymentAttempt;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.entity.Subscription;
import com.reviveai.recovery.RecoveryActionExecutor;
import com.reviveai.recovery.RecoveryDecision;
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

    private final RecoveryStrategyEngine recoveryStrategyEngine;
    private final RecoveryActionExecutor recoveryActionExecutor;


    @Transactional
    public void processPaymentFailure(PaymentFailedEvent event) {

        // =========================================================
        // 1. Validate event structure
        // =========================================================

        if (event == null ||
                event.getPayload() == null ||
                event.getPayload().getPayment() == null ||
                event.getPayload().getPayment().getEntity() == null) {

            log.warn(
                    "Ignoring invalid payment failure event"
            );

            return;
        }


        // =========================================================
        // 2. Extract payment entity
        // =========================================================

        PaymentFailedEvent.Entity payment =
                event.getPayload()
                        .getPayment()
                        .getEntity();


        // =========================================================
        // 3. Extract payment ID
        // =========================================================

        String paymentId =
                payment.getId();


        // =========================================================
        // 4. Extract subscription ID
        // =========================================================

        String subscriptionId =
                payment.getSubscriptionId();


        /*
         * Razorpay subscription payment events may provide
         * subscription information in:
         *
         * payload.payment.entity.subscription_id
         *
         * OR
         *
         * payload.subscription.entity.id
         *
         * Therefore use payload.subscription.entity.id
         * as a fallback.
         */

        if ((subscriptionId == null ||
                subscriptionId.isBlank()) &&

                event.getPayload().getSubscription() != null &&

                event.getPayload()
                        .getSubscription()
                        .getEntity() != null) {

            subscriptionId =
                    event.getPayload()
                            .getSubscription()
                            .getEntity()
                            .getId();
        }


        log.info(
                "Processing payment failure. paymentId={}, subscriptionId={}",
                paymentId,
                subscriptionId
        );


        // =========================================================
        // 5. Validate payment ID
        // =========================================================

        if (paymentId == null ||
                paymentId.isBlank()) {

            log.warn(
                    "Payment event does not contain payment ID"
            );

            return;
        }


        // =========================================================
        // 6. Validate subscription ID
        // =========================================================

        if (subscriptionId == null ||
                subscriptionId.isBlank()) {

            log.warn(
                    "Payment event does not contain subscription ID. paymentId={}",
                    paymentId
            );

            return;
        }


        // =========================================================
        // 7. Payment idempotency check
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
        // 8. Find subscription
        // =========================================================

        Subscription subscription =
                subscriptionRepository
                        .findByExternalSubscriptionId(
                                subscriptionId
                        )
                        .orElse(null);


        if (subscription == null) {

            log.warn(
                    "Subscription not found. externalSubscriptionId={}, paymentId={}",
                    subscriptionId,
                    paymentId
            );

            /*
             * Do not continue because PaymentAttempt,
             * RecoveryCase and subscription status all depend
             * on an existing subscription.
             */

            return;
        }


        log.info(
                "Subscription found. internalId={}, externalSubscriptionId={}",
                subscription.getId(),
                subscriptionId
        );


        // =========================================================
        // 9. Validate amount
        // =========================================================

        if (payment.getAmount() == null) {

            log.warn(
                    "Payment event does not contain amount. paymentId={}",
                    paymentId
            );

            return;
        }


        // =========================================================
        // 10. Convert Razorpay amount
        // =========================================================
        /*
         * Razorpay sends amount in the smallest currency unit.
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
                "Payment amount converted. paymentId={}, amount={}",
                paymentId,
                amount
        );


        // =========================================================
        // 11. Create PaymentAttempt
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
                "PaymentAttempt created. id={}, paymentId={}",
                paymentAttempt.getId(),
                paymentId
        );


        // =========================================================
        // 12. Mark subscription as PAST_DUE
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
        // 13. Determine recovery potential
        // =========================================================

        RecoveryCase.RecoveryPotential recoveryPotential =
                determineRecoveryPotential(amount);


        // =========================================================
        // 14. Calculate initial recovery score
        // =========================================================

        BigDecimal recoveryScore =
                calculateInitialRecoveryScore(
                        payment.getErrorCode()
                );


        log.info(
                "Recovery metrics calculated. " +
                        "paymentId={}, potential={}, score={}",
                paymentId,
                recoveryPotential,
                recoveryScore
        );


        // =========================================================
        // 15. Create RecoveryCase
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
                        .recoveryScore(
                                recoveryScore
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
                        "recoveryCaseId={}, paymentId={}, amountAtRisk={}",
                recoveryCase.getId(),
                paymentId,
                amount
        );


        // =========================================================
        // 16. Determine recovery strategy
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

            return;
        }


        log.info(
                "Recovery strategy determined. " +
                        "recoveryCaseId={}, " +
                        "paymentId={}, " +
                        "strategy={}, " +
                        "priority={}, " +
                        "score={}",

                recoveryCase.getId(),
                paymentId,
                decision.getStrategy(),
                decision.getPriority(),
                decision.getRecoveryScore()
        );


        // =========================================================
        // 17. Execute recovery action
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
    // Recovery Potential
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


    // =============================================================
    // Initial Recovery Score
    // =============================================================

    private BigDecimal calculateInitialRecoveryScore(
            String errorCode
    ) {

        if (errorCode == null ||
                errorCode.isBlank()) {

            return new BigDecimal("0.50");
        }


        return switch (errorCode) {

            case "INSUFFICIENT_FUNDS" ->
                    new BigDecimal("0.70");

            case "CARD_EXPIRED" ->
                    new BigDecimal("0.80");

            case "CARD_DECLINED" ->
                    new BigDecimal("0.50");

            case "AUTHENTICATION_FAILED" ->
                    new BigDecimal("0.40");

            default ->
                    new BigDecimal("0.50");
        };
    }
}