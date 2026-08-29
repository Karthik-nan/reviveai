package com.reviveai.ml;

import com.reviveai.entity.PaymentAttempt;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.entity.Subscription;
import com.reviveai.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;

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

        // =========================================================
        // 1. CURRENT PAYMENT TIME
        // =========================================================

        OffsetDateTime currentAttemptTime =
                OffsetDateTime.now();

        if (recoveryCase.getFailedPayment() != null
                && recoveryCase
                .getFailedPayment()
                .getAttemptedAt() != null) {

            currentAttemptTime =
                    recoveryCase
                            .getFailedPayment()
                            .getAttemptedAt();
        }

        // =========================================================
        // 2. PAYMENT AMOUNT
        // =========================================================

        BigDecimal paymentAmount =
                recoveryCase.getAmountAtRisk();

        if (paymentAmount == null) {
            paymentAmount = BigDecimal.ZERO;
        }

        // =========================================================
        // 3. PREVIOUS SUCCESSFUL PAYMENTS
        // =========================================================

        long previousSuccessfulPayments =
                paymentAttemptRepository
                        .countBySubscriptionIdAndStatusAndAttemptedAtBefore(
                                subscription.getId(),
                                PaymentAttempt.PaymentStatus.SUCCESS,
                                currentAttemptTime
                        );

        // =========================================================
        // 4. PREVIOUS FAILED PAYMENTS
        // =========================================================
        //
        // IMPORTANT:
        // Only payments BEFORE the current failed payment
        // are counted.
        //
        // This prevents the current failure from contaminating
        // the historical ML features.
        // =========================================================

        long previousFailedPayments =
                paymentAttemptRepository
                        .countBySubscriptionIdAndStatusAndAttemptedAtBefore(
                                subscription.getId(),
                                PaymentAttempt.PaymentStatus.FAILED,
                                currentAttemptTime
                        );

        // =========================================================
        // 5. TOTAL PREVIOUS PAYMENTS
        // =========================================================

        long totalPreviousPayments =
                previousSuccessfulPayments
                        + previousFailedPayments;

        // =========================================================
        // 6. PAYMENT FAILURE RATE
        // =========================================================

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
        // 7. RETRY COUNT
        // =========================================================
        //
        // Current data model does not have a dedicated retry_count
        // column.
        //
        // Therefore previous failed attempts are used as the
        // current retry-count signal.
        // =========================================================

        int retryCount =
                (int) Math.min(
                        previousFailedPayments,
                        Integer.MAX_VALUE
                );

        // =========================================================
        // 8. DAYS PAST DUE
        // =========================================================
        //
        // nextBillingAt represents the expected billing time.
        //
        // If current time is after nextBillingAt, calculate the
        // number of complete days since the billing date.
        // =========================================================

        int daysPastDue = 0;

        if (subscription.getNextBillingAt() != null) {

            OffsetDateTime now =
                    OffsetDateTime.now();

            if (now.isAfter(
                    subscription.getNextBillingAt()
            )) {

                long days =
                        Duration.between(
                                subscription.getNextBillingAt(),
                                now
                        ).toDays();

                daysPastDue =
                        (int) Math.min(
                                Math.max(days, 0),
                                Integer.MAX_VALUE
                        );
            }
        }

        // =========================================================
        // 9. RECOVERY POTENTIAL
        // =========================================================

        String recoveryPotential =
                recoveryCase.getRecoveryPotential() != null
                        ? recoveryCase
                        .getRecoveryPotential()
                        .name()
                        : "MEDIUM";

        // =========================================================
        // 10. GATEWAY ERROR CODE
        // =========================================================

        String errorCode = null;

        if (recoveryCase.getFailedPayment() != null) {

            errorCode =
                    recoveryCase
                            .getFailedPayment()
                            .getGatewayErrorCode();
        }

        // =========================================================
        // 11. BUILD ML REQUEST
        // =========================================================

        return RecoveryPredictionRequest.builder()

                .paymentAmount(
                        paymentAmount
                )

                .retryCount(
                        retryCount
                )

                .daysPastDue(
                        daysPastDue
                )

                .previousSuccessfulPayments(
                        (int) Math.min(
                                previousSuccessfulPayments,
                                Integer.MAX_VALUE
                        )
                )

                .previousFailedPayments(
                        (int) Math.min(
                                previousFailedPayments,
                                Integer.MAX_VALUE
                        )
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