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
    public void processPaymentFailure(
            PaymentFailedEvent event
    ) {

        if (event == null ||
                event.getPayload() == null ||
                event.getPayload().getPayment() == null) {

            log.warn(
                    "Ignoring invalid payment failure event"
            );

            return;
        }

        PaymentFailedEvent.Payment payment =
                event.getPayload().getPayment();

        String paymentId =
                payment.getId();

        String subscriptionId =
                payment.getSubscriptionId();

        log.info(
                "Processing payment failure. paymentId={}, subscriptionId={}",
                paymentId,
                subscriptionId
        );

        // ---------------------------------------------
        // 1. Validate payment ID
        // ---------------------------------------------

        if (paymentId == null ||
                paymentId.isBlank()) {

            log.warn(
                    "Payment event does not contain payment ID"
            );

            return;
        }

        // ---------------------------------------------
        // 2. Validate subscription ID
        // ---------------------------------------------

        if (subscriptionId == null ||
                subscriptionId.isBlank()) {

            log.warn(
                    "Payment event does not contain subscription ID. paymentId={}",
                    paymentId
            );

            return;
        }

        // ---------------------------------------------
        // 3. Idempotency check
        // ---------------------------------------------

        if (paymentAttemptRepository
                .findByIdempotencyKey(paymentId)
                .isPresent()) {

            log.info(
                    "Payment already processed. paymentId={}",
                    paymentId
            );

            return;
        }

        // ---------------------------------------------
        // 4. Find subscription
        // ---------------------------------------------

        Subscription subscription =
                subscriptionRepository
                        .findByExternalSubscriptionId(
                                subscriptionId
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Subscription not found: "
                                                + subscriptionId
                                )
                        );

        // ---------------------------------------------
        // 5. Convert amount
        // ---------------------------------------------

        BigDecimal amount =
                BigDecimal.valueOf(
                        payment.getAmount()
                ).movePointLeft(2);

        // ---------------------------------------------
        // 6. Create payment attempt
        // ---------------------------------------------

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

        // ---------------------------------------------
        // 7. Mark subscription as PAST_DUE
        // ---------------------------------------------

        subscription.setStatus(
                Subscription.SubscriptionStatus.PAST_DUE
        );

        subscriptionRepository.save(
                subscription
        );

        log.info(
                "Subscription marked PAST_DUE. subscriptionId={}",
                subscription.getId()
        );

        // ---------------------------------------------
        // 8. Create recovery case
        // ---------------------------------------------

        RecoveryCase recoveryCase =
                RecoveryCase.builder()
                        .subscription(subscription)
                        .failedPayment(paymentAttempt)
                        .status(
                                RecoveryCase.RecoveryStatus.OPEN
                        )
                        .recoveryPotential(
                                determineRecoveryPotential(
                                        amount
                                )
                        )
                        .recoveryScore(
                                calculateInitialRecoveryScore(
                                        payment.getErrorCode()
                                )
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
                "Recovery case created. recoveryCaseId={}, paymentId={}",
                recoveryCase.getId(),
                paymentId
        );

        // ---------------------------------------------
        // 9. Determine recovery strategy
        // ---------------------------------------------

        RecoveryDecision decision =
                recoveryStrategyEngine.determineStrategy(
                        recoveryCase
                );

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

        // ---------------------------------------------
        // 10. Execute recovery action
        // ---------------------------------------------

        recoveryActionExecutor.execute(
                recoveryCase,decision
        );

        log.info(
                "Recovery action execution completed. " +
                        "recoveryCaseId={}, strategy={}",
                recoveryCase.getId(),
                decision.getStrategy()
        );
    }

// ---------------------------------------------
// Recovery potential
// ---------------------------------------------

    private RecoveryCase.RecoveryPotential
    determineRecoveryPotential(
            BigDecimal amount
    ) {

        if (amount == null) {

            return RecoveryCase.RecoveryPotential.MEDIUM;
        }

        if (amount.compareTo(
                new BigDecimal("5000")
        ) <= 0) {

            return RecoveryCase.RecoveryPotential.HIGH;

        } else if (amount.compareTo(
                new BigDecimal("20000")
        ) <= 0) {

            return RecoveryCase.RecoveryPotential.MEDIUM;

        } else {

            return RecoveryCase.RecoveryPotential.LOW;
        }
    }

// ---------------------------------------------
// Initial recovery score
// ---------------------------------------------

    private BigDecimal calculateInitialRecoveryScore(
            String errorCode
    ) {

        if (errorCode == null) {

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
