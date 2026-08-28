package com.reviveai.service;

import com.reviveai.entity.PaymentAttempt;
import com.reviveai.entity.Subscription;
import com.reviveai.entity.SubscriptionHealth;
import com.reviveai.repository.PaymentAttemptRepository;
import com.reviveai.repository.SubscriptionHealthRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionHealthEvaluatorImpl
        implements SubscriptionHealthEvaluator {

    private static final int RECENT_FAILURE_WINDOW_DAYS = 30;

    private final PaymentAttemptRepository paymentAttemptRepository;

    private final SubscriptionHealthRepository
            subscriptionHealthRepository;

    @Override
    @Transactional
    public SubscriptionHealth evaluateHealth(
            Subscription subscription
    ) {

        if (subscription == null || subscription.getId() == null) {

            throw new IllegalArgumentException(
                    "Subscription cannot be null"
            );
        }

        /*
         * ============================================================
         * 1. SUBSCRIPTION ID
         * ============================================================
         */

        var subscriptionId =
                subscription.getId();

        /*
         * ============================================================
         * 2. TOTAL SUCCESSFUL PAYMENTS
         * ============================================================
         */

        long successfulPayments =
                paymentAttemptRepository
                        .countBySubscriptionIdAndStatus(
                                subscriptionId,
                                PaymentAttempt.PaymentStatus.SUCCESS
                        );

        /*
         * ============================================================
         * 3. TOTAL FAILED PAYMENTS
         * ============================================================
         */

        long failedPayments =
                paymentAttemptRepository
                        .countBySubscriptionIdAndStatus(
                                subscriptionId,
                                PaymentAttempt.PaymentStatus.FAILED
                        );

        /*
         * ============================================================
         * 4. PAYMENT FAILURE RATE
         * ============================================================
         *
         * failed / (successful + failed)
         */

        long totalPayments =
                successfulPayments + failedPayments;

        BigDecimal failureRate =
                calculateFailureRate(
                        failedPayments,
                        totalPayments
                );

        /*
         * ============================================================
         * 5. RECENT FAILURE COUNT
         * ============================================================
         *
         * Look at failures during the last 30 days.
         */

        OffsetDateTime recentFailureCutoff =
                OffsetDateTime.now()
                        .minusDays(
                                RECENT_FAILURE_WINDOW_DAYS
                        );

        long recentFailures =
                paymentAttemptRepository
                        .countBySubscriptionIdAndStatusAndAttemptedAtAfter(
                                subscriptionId,
                                PaymentAttempt.PaymentStatus.FAILED,
                                recentFailureCutoff
                        );

        /*
         * ============================================================
         * 6. CONSECUTIVE FAILURES
         * ============================================================
         */

        int consecutiveFailures =
                calculateConsecutiveFailures(
                        subscriptionId
                );

        /*
         * ============================================================
         * 7. PAYMENT BEHAVIOR DECLINE
         * ============================================================
         */

        boolean paymentBehaviorDeclining =
                calculatePaymentBehaviorDeclining(
                        consecutiveFailures,
                        recentFailures,
                        failureRate
                );

        /*
         * ============================================================
         * 8. HEALTH SCORE
         * ============================================================
         *
         * 1.00 = very healthy
         * 0.00 = very unhealthy
         */

        BigDecimal healthScore =
                calculateHealthScore(
                        successfulPayments,
                        failedPayments,
                        failureRate,
                        consecutiveFailures,
                        recentFailures
                );

        /*
         * ============================================================
         * 9. RISK LEVEL
         * ============================================================
         */

        SubscriptionHealth.RiskLevel riskLevel =
                determineRiskLevel(
                        healthScore
                );

        /*
         * ============================================================
         * 10. FIND EXISTING HEALTH RECORD
         * ============================================================
         */

        SubscriptionHealth health =
                subscriptionHealthRepository
                        .findBySubscriptionId(subscriptionId)
                        .orElseGet(() ->
                                SubscriptionHealth.builder()
                                        .subscription(subscription)
                                        .build()
                        );

        /*
         * ============================================================
         * 11. UPDATE HEALTH RECORD
         * ============================================================
         */

        health.setSubscription(subscription);

        health.setHealthScore(
                healthScore
        );

        health.setRiskLevel(
                riskLevel
        );

        health.setSuccessfulPaymentCount(
                toInteger(successfulPayments)
        );

        health.setFailedPaymentCount(
                toInteger(failedPayments)
        );

        health.setPaymentFailureRate(
                failureRate
        );

        health.setConsecutiveFailures(
                consecutiveFailures
        );

        health.setRecentFailureCount(
                toInteger(recentFailures)
        );

        health.setPaymentBehaviorDeclining(
                paymentBehaviorDeclining
        );

        health.setLastEvaluatedAt(
                java.time.LocalDateTime.now()
        );

        /*
         * ============================================================
         * 12. SAVE
         * ============================================================
         */

        SubscriptionHealth savedHealth =
                subscriptionHealthRepository.save(
                        health
                );

        log.info(
                "Subscription health evaluated. " +
                        "subscriptionId={}, " +
                        "healthScore={}, " +
                        "riskLevel={}, " +
                        "successfulPayments={}, " +
                        "failedPayments={}, " +
                        "failureRate={}, " +
                        "consecutiveFailures={}, " +
                        "recentFailures={}, " +
                        "paymentBehaviorDeclining={}",
                subscriptionId,
                healthScore,
                riskLevel,
                successfulPayments,
                failedPayments,
                failureRate,
                consecutiveFailures,
                recentFailures,
                paymentBehaviorDeclining
        );

        return savedHealth;
    }

    // ================================================================
    // FAILURE RATE
    // ================================================================

    private BigDecimal calculateFailureRate(
            long failedPayments,
            long totalPayments
    ) {

        if (totalPayments == 0) {

            return BigDecimal.ZERO;
        }

        return BigDecimal
                .valueOf(failedPayments)
                .divide(
                        BigDecimal.valueOf(totalPayments),
                        2,
                        RoundingMode.HALF_UP
                );
    }

    // ================================================================
    // CONSECUTIVE FAILURES
    // ================================================================

    private int calculateConsecutiveFailures(
            java.util.UUID subscriptionId
    ) {

        List<PaymentAttempt> attempts =
                paymentAttemptRepository
                        .findBySubscriptionIdOrderByAttemptedAtDesc(
                                subscriptionId
                        );

        int consecutiveFailures = 0;

        for (PaymentAttempt attempt : attempts) {

            if (attempt.getStatus()
                    == PaymentAttempt.PaymentStatus.FAILED) {

                consecutiveFailures++;

            } else {

                break;
            }
        }

        return consecutiveFailures;
    }

    // ================================================================
    // PAYMENT BEHAVIOR DECLINE
    // ================================================================

    private boolean calculatePaymentBehaviorDeclining(
            int consecutiveFailures,
            long recentFailures,
            BigDecimal failureRate
    ) {

        /*
         * Multiple consecutive failures are a strong
         * deterioration signal.
         */

        if (consecutiveFailures >= 2) {

            return true;
        }

        /*
         * Several failures during the recent window
         * indicate worsening payment behavior.
         */

        if (recentFailures >= 3) {

            return true;
        }

        /*
         * High overall failure rate is another signal.
         */

        return failureRate.compareTo(
                new BigDecimal("0.50")
        ) > 0;
    }

    // ================================================================
    // HEALTH SCORE
    // ================================================================

    private BigDecimal calculateHealthScore(
            long successfulPayments,
            long failedPayments,
            BigDecimal failureRate,
            int consecutiveFailures,
            long recentFailures
    ) {

        double score = 1.00;

        /*
         * Payment failure rate.
         */

        score -=
                failureRate
                        .doubleValue() * 0.50;

        /*
         * Consecutive failures.
         */

        if (consecutiveFailures >= 1) {

            score -= 0.10;
        }

        if (consecutiveFailures >= 2) {

            score -= 0.10;
        }

        if (consecutiveFailures >= 3) {

            score -= 0.15;
        }

        /*
         * Recent failures.
         */

        if (recentFailures >= 2) {

            score -= 0.05;
        }

        if (recentFailures >= 4) {

            score -= 0.10;
        }

        /*
         * Successful payment history gives
         * a small stability boost.
         */

        if (successfulPayments >= 5 &&
                failureRate.compareTo(
                        new BigDecimal("0.20")
                ) < 0) {

            score += 0.05;
        }

        /*
         * Keep score within [0.00, 1.00].
         */

        score =
                Math.max(
                        0.0,
                        Math.min(
                                1.0,
                                score
                        )
                );

        return BigDecimal
                .valueOf(score)
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }

    // ================================================================
    // RISK LEVEL
    // ================================================================

    private SubscriptionHealth.RiskLevel determineRiskLevel(
            BigDecimal healthScore
    ) {

        if (healthScore.compareTo(
                new BigDecimal("0.80")
        ) >= 0) {

            return SubscriptionHealth.RiskLevel.HEALTHY;
        }

        if (healthScore.compareTo(
                new BigDecimal("0.60")
        ) >= 0) {

            return SubscriptionHealth.RiskLevel.LOW;
        }

        if (healthScore.compareTo(
                new BigDecimal("0.40")
        ) >= 0) {

            return SubscriptionHealth.RiskLevel.MEDIUM;
        }

        if (healthScore.compareTo(
                new BigDecimal("0.20")
        ) >= 0) {

            return SubscriptionHealth.RiskLevel.HIGH;
        }

        return SubscriptionHealth.RiskLevel.CRITICAL;
    }

    // ================================================================
    // LONG → INTEGER
    // ================================================================

    private int toInteger(long value) {

        if (value > Integer.MAX_VALUE) {

            return Integer.MAX_VALUE;
        }

        return (int) value;
    }
}
