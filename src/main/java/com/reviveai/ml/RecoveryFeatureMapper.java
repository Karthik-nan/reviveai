package com.reviveai.ml;

import com.reviveai.entity.PaymentAttempt;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.entity.Subscription;
import com.reviveai.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
public class RecoveryFeatureMapper {

    private final PaymentAttemptRepository paymentAttemptRepository;

    public RecoveryPredictionRequest map(
            RecoveryCase recoveryCase
    ) {

        if (recoveryCase == null) {
            throw new IllegalArgumentException(
                    "Recovery case cannot be null"
            );
        }

        Subscription subscription =
                recoveryCase.getSubscription();

        if (subscription == null) {
            throw new IllegalArgumentException(
                    "Recovery case subscription cannot be null"
            );
        }

        BigDecimal paymentAmount =
                recoveryCase.getAmountAtRisk();

        if (paymentAmount == null) {
            paymentAmount = BigDecimal.ZERO;
        }

        // =========================================================
        // Historical payment statistics
        // =========================================================

        long previousSuccessfulPayments =
                paymentAttemptRepository
                        .countBySubscriptionIdAndStatus(
                                subscription.getId(),
                                PaymentAttempt.PaymentStatus.SUCCESS
                        );

        long previousFailedPayments =
                paymentAttemptRepository
                        .countBySubscriptionIdAndStatus(
                                subscription.getId(),
                                PaymentAttempt.PaymentStatus.FAILED
                        );

        long totalPreviousPayments =
                previousSuccessfulPayments
                        + previousFailedPayments;

        BigDecimal paymentFailureRate =
                BigDecimal.ZERO;

        if (totalPreviousPayments > 0) {

            paymentFailureRate =
                    BigDecimal.valueOf(
                                    previousFailedPayments
                            )
                            .divide(
                                    BigDecimal.valueOf(
                                            totalPreviousPayments
                                    ),
                                    2,
                                    RoundingMode.HALF_UP
                            );
        }

        // =========================================================
        // Current retry count
        // =========================================================

        int retryCount = 0;

        // =========================================================
        // Days past due
        // =========================================================

        int daysPastDue = 0;

        // =========================================================
        // Recovery potential
        // =========================================================

        String recoveryPotential =
                recoveryCase.getRecoveryPotential() != null
                        ? recoveryCase
                        .getRecoveryPotential()
                        .name()
                        : "MEDIUM";

        // =========================================================
        // Gateway error code
        // =========================================================

        String errorCode = null;

        if (recoveryCase.getFailedPayment() != null) {

            errorCode =
                    recoveryCase
                            .getFailedPayment()
                            .getGatewayErrorCode();
        }

        // =========================================================
        // Build ML feature request
        // =========================================================

        return RecoveryPredictionRequest.builder()

                .paymentAmount(paymentAmount)

                .retryCount(retryCount)

                .daysPastDue(daysPastDue)

                .previousSuccessfulPayments(
                        (int) previousSuccessfulPayments
                )

                .previousFailedPayments(
                        (int) previousFailedPayments
                )

                .paymentFailureRate(
                        paymentFailureRate
                )

                .recoveryPotential(
                        recoveryPotential
                )

                .errorCode(
                        errorCode
                )

                .build();
    }
}