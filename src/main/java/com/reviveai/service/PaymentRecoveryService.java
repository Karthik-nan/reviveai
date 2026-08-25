package com.reviveai.service;

import com.reviveai.dto.PaymentFailedEvent;
import com.reviveai.entity.PaymentAttempt;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.entity.Subscription;
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

    @Transactional
    public void processPaymentFailure(PaymentFailedEvent event) {

        if (event == null ||
                event.getPayload() == null ||
                event.getPayload().getPayment() == null) {

            log.warn("Ignoring invalid payment failure event");
            return;
        }

        PaymentFailedEvent.Payment payment =
                event.getPayload().getPayment();

        String externalPaymentId = payment.getId();
        String externalSubscriptionId = payment.getSubscriptionId();

        log.info(
                "Processing payment failure. paymentId={}, subscriptionId={}",
                externalPaymentId,
                externalSubscriptionId
        );

        // ----------------------------------------------------
        // 1. Validate required fields
        // ----------------------------------------------------

        if (externalPaymentId == null || externalPaymentId.isBlank()) {

            log.warn("Payment event does not contain payment ID");
            return;
        }

        if (externalSubscriptionId == null ||
                externalSubscriptionId.isBlank()) {

            log.warn(
                    "Payment event does not contain subscription ID. paymentId={}",
                    externalPaymentId
            );

            return;
        }

        // ----------------------------------------------------
        // 2. Check whether payment was already processed
        // ----------------------------------------------------

        if (paymentAttemptRepository
                .findByIdempotencyKey(externalPaymentId)
                .isPresent()) {

            log.info(
                    "Payment already processed. paymentId={}",
                    externalPaymentId
            );

            return;
        }

        // ----------------------------------------------------
        // 3. Find subscription
        // ----------------------------------------------------

        Subscription subscription =
                subscriptionRepository
                        .findByExternalSubscriptionId(
                                externalSubscriptionId
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Subscription not found: "
                                                + externalSubscriptionId
                                )
                        );

        // ----------------------------------------------------
        // 4. Create failed payment attempt
        // ----------------------------------------------------

        BigDecimal amount =
                BigDecimal.valueOf(payment.getAmount())
                        .movePointLeft(2);

        PaymentAttempt paymentAttempt =
                PaymentAttempt.builder()
                        .subscription(subscription)
                        .externalPaymentId(externalPaymentId)
                        .idempotencyKey(externalPaymentId)
                        .amount(amount)
                        .status(PaymentAttempt.PaymentStatus.FAILED)
                        .gatewayErrorCode(payment.getErrorCode())
                        .gatewayErrorMessage(
                                payment.getErrorDescription()
                        )
                        .build();

        paymentAttempt =
                paymentAttemptRepository.save(paymentAttempt);

        log.info(
                "PaymentAttempt created. id={}, paymentId={}",
                paymentAttempt.getId(),
                externalPaymentId
        );

        // ----------------------------------------------------
        // 5. Update subscription status
        // ----------------------------------------------------

        subscription.setStatus(
                Subscription.SubscriptionStatus.PAST_DUE
        );

        subscriptionRepository.save(subscription);

        log.info(
                "Subscription marked PAST_DUE. subscriptionId={}",
                subscription.getId()
        );

        // ----------------------------------------------------
        // 6. Create recovery case
        // ----------------------------------------------------

        RecoveryCase recoveryCase =
                RecoveryCase.builder()
                        .subscription(subscription)
                        .failedPayment(paymentAttempt)
                        .status(RecoveryCase.RecoveryStatus.OPEN)
                        .recoveryPotential(
                                determineRecoveryPotential(amount)
                        )
                        .recoveryScore(
                                calculateInitialRecoveryScore(
                                        payment.getErrorCode()
                                )
                        )
                        .amountAtRisk(amount)
                        .amountRecovered(BigDecimal.ZERO)
                        .build();

        recoveryCase =
                recoveryCaseRepository.save(recoveryCase);

        log.info(
                "Recovery case created. recoveryCaseId={}, paymentId={}",
                recoveryCase.getId(),
                externalPaymentId
        );
    }

    // --------------------------------------------------------
    // Recovery potential
    // --------------------------------------------------------

    private RecoveryCase.RecoveryPotential determineRecoveryPotential(
            BigDecimal amount
    ) {

        if (amount == null) {
            return RecoveryCase.RecoveryPotential.MEDIUM;
        }

        /*
         * Initial rule only.
         *
         * Later this can be replaced by an AI/risk model.
         */

        if (amount.compareTo(new BigDecimal("5000")) <= 0) {

            return RecoveryCase.RecoveryPotential.HIGH;

        } else if (amount.compareTo(new BigDecimal("20000")) <= 0) {

            return RecoveryCase.RecoveryPotential.MEDIUM;

        } else {

            return RecoveryCase.RecoveryPotential.LOW;
        }
    }

    // --------------------------------------------------------
    // Initial recovery score
    // --------------------------------------------------------

    private BigDecimal calculateInitialRecoveryScore(
            String errorCode
    ) {

        if (errorCode == null) {
            return new BigDecimal("0.50");
        }

        /*
         * Temporary deterministic scoring.
         *
         * This is NOT the final AI recovery score.
         */

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

