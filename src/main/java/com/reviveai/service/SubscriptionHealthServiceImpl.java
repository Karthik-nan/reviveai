package com.reviveai.service;

import com.reviveai.dto.RazorpayWebhookPayload;
import com.reviveai.entity.*;
import com.reviveai.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionHealthServiceImpl implements SubscriptionHealthService {

    private final MerchantRepository merchantRepository;
    private final CustomerRepository customerRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final RecoveryCaseRepository recoveryCaseRepository;

    @Override
    @Transactional
    public RecoveryCase processPaymentFailure(RazorpayWebhookPayload payload) {

        RazorpayWebhookPayload.PaymentEntity paymentData =
                payload.getPayload()
                        .getPayment()
                        .getEntity();

        // ============================================================
        // 1. FETCH OR CREATE MERCHANT
        // ============================================================

        Merchant merchant = merchantRepository.findAll()
                .stream()
                .findFirst()
                .orElseGet(() ->
                        merchantRepository.save(
                                Merchant.builder()
                                        .name("Demo Merchant")
                                        .apiKeyHash("demo_hash_key")
                                        .build()
                        )
                );

        // ============================================================
        // 2. FETCH OR CREATE CUSTOMER
        // ============================================================

        String extCustId = paymentData.getCustomerId() != null
                ? paymentData.getCustomerId()
                : "cust_default";

        Customer customer =
                customerRepository
                        .findByMerchantIdAndExternalCustomerId(
                                merchant.getId(),
                                extCustId
                        )
                        .orElseGet(() ->
                                customerRepository.save(
                                        Customer.builder()
                                                .merchant(merchant)
                                                .externalCustomerId(extCustId)
                                                .email(
                                                        "customer_"
                                                                + extCustId
                                                                + "@example.com"
                                                )
                                                .build()
                                )
                        );

        // ============================================================
        // 3. FETCH OR CREATE SUBSCRIPTION
        // ============================================================

        String extSubId = paymentData.getSubscriptionId() != null
                ? paymentData.getSubscriptionId()
                : "sub_default";

        BigDecimal amount =
                BigDecimal.valueOf(paymentData.getAmount())
                        .divide(
                                BigDecimal.valueOf(100),
                                2,
                                RoundingMode.HALF_UP
                        );

        Subscription subscription =
                subscriptionRepository
                        .findByExternalSubscriptionId(extSubId)
                        .orElseGet(() ->
                                subscriptionRepository.save(
                                        Subscription.builder()
                                                .customer(customer)
                                                .externalSubscriptionId(extSubId)

                                                // ENUM
                                                .status(
                                                        Subscription.SubscriptionStatus.PAST_DUE
                                                )

                                                .amount(amount)
                                                .currency(
                                                        paymentData.getCurrency() != null
                                                                ? paymentData.getCurrency()
                                                                : "INR"
                                                )
                                                .build()
                                )
                        );

        // ============================================================
        // 4. PERSIST PAYMENT ATTEMPT
        // ============================================================

        PaymentAttempt attempt =
                paymentAttemptRepository.save(
                        PaymentAttempt.builder()
                                .subscription(subscription)
                                .externalPaymentId(paymentData.getId())
                                .idempotencyKey(
                                        "idemp_" + UUID.randomUUID()
                                )
                                .amount(amount)

                                // ENUM
                                .status(
                                        PaymentAttempt.PaymentStatus.FAILED
                                )

                                .gatewayErrorCode(
                                        paymentData.getErrorCode()
                                )
                                .gatewayErrorMessage(
                                        paymentData.getErrorDescription()
                                )
                                .build()
                );

        // ============================================================
        // 5. CALCULATE INITIAL RISK SCORE
        // ============================================================

        BigDecimal riskScore =
                calculateInitialRiskScore(
                        paymentData.getErrorCode(),
                        amount
                );

        // ============================================================
        // 6. DETERMINE RECOVERY POTENTIAL
        // ============================================================

        RecoveryCase.RecoveryPotential potential;

        if (riskScore.compareTo(BigDecimal.valueOf(0.70)) >= 0) {

            potential =
                    RecoveryCase.RecoveryPotential.HIGH;

        } else if (riskScore.compareTo(BigDecimal.valueOf(0.40)) >= 0) {

            potential =
                    RecoveryCase.RecoveryPotential.MEDIUM;

        } else {

            potential =
                    RecoveryCase.RecoveryPotential.LOW;
        }

        // ============================================================
        // 7. UPDATE SUBSCRIPTION RISK SCORE
        // ============================================================

        subscription.setRiskScore(riskScore);

        subscriptionRepository.save(subscription);

        // ============================================================
        // 8. CREATE RECOVERY CASE
        // ============================================================

        RecoveryCase recoveryCase =
                recoveryCaseRepository.save(
                        RecoveryCase.builder()
                                .subscription(subscription)
                                .failedPayment(attempt)

                                // ENUM
                                .status(
                                        RecoveryCase.RecoveryStatus.OPEN
                                )

                                .recoveryPotential(potential)
                                .recoveryScore(riskScore)
                                .amountAtRisk(amount)
                                .build()
                );

        // ============================================================
        // 9. LOG
        // ============================================================

        log.info(
                "Opened RecoveryCase ID: {} for Subscription: {} with Risk Score: {}",
                recoveryCase.getId(),
                extSubId,
                riskScore
        );

        return recoveryCase;
    }

    // ================================================================
    // RISK SCORE CALCULATION
    // ================================================================

    private BigDecimal calculateInitialRiskScore(
            String errorCode,
            BigDecimal amount
    ) {

        // Default: high recovery probability
        double score = 0.85;

        // Hard decline
        if (
                "BAD_CARD_EXPIRED".equalsIgnoreCase(errorCode)
                        || "CARD_BLOCKED".equalsIgnoreCase(errorCode)
        ) {

            score = 0.20;

        }

        // Temporary financial issue
        else if (
                "INSUFFICIENT_FUNDS".equalsIgnoreCase(errorCode)
        ) {

            score = 0.60;
        }

        // High-value payment adjustment
        if (
                amount.compareTo(BigDecimal.valueOf(10000)) > 0
        ) {

            score -= 0.10;
        }

        // Keep score between 0 and 1
        score = Math.max(
                0.0,
                Math.min(1.0, score)
        );

        return BigDecimal
                .valueOf(score)
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }
}