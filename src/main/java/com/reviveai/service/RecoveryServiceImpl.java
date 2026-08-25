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
public class RecoveryServiceImpl implements RecoveryService {

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final RecoveryCaseRepository recoveryCaseRepository;

    @Override
    @Transactional
    public void processPaymentFailure(PaymentFailedEvent event) {

        if (event == null ||
                event.getPayload() == null ||
                event.getPayload().getPayment() == null) {

            log.warn("Invalid payment failure event");

            return;
        }

        PaymentFailedEvent.Payment payment =
                event.getPayload().getPayment();

        log.info(
                "Processing payment failure. paymentId={}, customerId={}",
                payment.getId(),
                payment.getCustomerId()
        );

        // ----------------------------------------------------
        // 1. Validate required payment information
        // ----------------------------------------------------

        if (payment.getId() == null || payment.getId().isBlank()) {

            log.warn("Payment event does not contain payment ID");

            return;
        }

        if (payment.getSubscriptionId() == null ||
                payment.getSubscriptionId().isBlank()) {

            log.warn(
                    "Payment event does not contain subscription ID. paymentId={}",
                    payment.getId()
            );

            return;
        }

        if (payment.getAmount() == null) {

            log.warn(
                    "Payment event does not contain amount. paymentId={}",
                    payment.getId()
            );

            return;
        }

        // ----------------------------------------------------
        // 2. Check whether this payment was already processed
        // ----------------------------------------------------

        if (paymentAttemptRepository
                .findByIdempotencyKey(payment.getId())
                .isPresent()) {

            log.info(
                    "Payment already processed. paymentId={}",
                    payment.getId()
            );

            return;
        }

        // ----------------------------------------------------
        // 3. Find subscription
        // ----------------------------------------------------

        Subscription subscription =
                subscriptionRepository
                        .findByExternalSubscriptionId(
                                payment.getSubscriptionId()
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Subscription not found: "
                                                + payment.getSubscriptionId()
                                )
                        );

        // ----------------------------------------------------
        // 4. Convert amount from paise to rupees
        // ----------------------------------------------------

        BigDecimal amount =
                BigDecimal.valueOf(payment.getAmount())
                        .movePointLeft(2);

        // ----------------------------------------------------
        // 5. Create PaymentAttempt
        // ----------------------------------------------------

        PaymentAttempt paymentAttempt =
                PaymentAttempt.builder()
                        .subscription(subscription)
                        .externalPaymentId(payment.getId())
                        .idempotencyKey(payment.getId())
                        .amount(amount)
                        .status(PaymentAttempt.PaymentStatus.FAILED)
                        .gatewayErrorCode(payment.getErrorCode())
                        .gatewayErrorMessage(
                                payment.getErrorDescription()
                        )
                        .build();

        paymentAttempt =
                paymentAttemptRepository.save(paymentAttempt);

        // ----------------------------------------------------
        // 6. Create RecoveryCase
        // ----------------------------------------------------

        RecoveryCase recoveryCase =
                RecoveryCase.builder()
                        .subscription(subscription)
                        .failedPayment(paymentAttempt)
                        .status(
                                RecoveryCase.RecoveryStatus.OPEN
                        )
                        .recoveryPotential(
                                RecoveryCase.RecoveryPotential.MEDIUM
                        )
                        .recoveryScore(BigDecimal.ZERO)
                        .amountAtRisk(amount)
                        .amountRecovered(BigDecimal.ZERO)
                        .build();

        recoveryCase =
                recoveryCaseRepository.save(recoveryCase);

        // ----------------------------------------------------
        // 7. Log result
        // ----------------------------------------------------

        log.info(
                "Recovery case created successfully. " +
                        "paymentId={}, recoveryCaseId={}, amountAtRisk={}",
                payment.getId(),
                recoveryCase.getId(),
                amount
        );
    }
}
