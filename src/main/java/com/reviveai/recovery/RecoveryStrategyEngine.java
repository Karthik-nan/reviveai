package com.reviveai.recovery;

import com.reviveai.entity.PaymentAttempt;
import com.reviveai.entity.RecoveryCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class RecoveryStrategyEngine {

    public RecoveryDecision determineStrategy(RecoveryCase recoveryCase) {

        if (recoveryCase == null) {
            throw new IllegalArgumentException(
                    "Recovery case cannot be null"
            );
        }

        PaymentAttempt payment = recoveryCase.getFailedPayment();

        if (payment == null) {
            return RecoveryDecision.builder()
                    .strategy(RecoveryStrategy.MANUAL_REVIEW)
                    .priority(RecoveryPriority.LOW)
                    .recoveryScore(BigDecimal.ZERO)
                    .reason("Failed payment information is missing")
                    .build();
        }

        String errorCode = payment.getGatewayErrorCode();

        BigDecimal score = recoveryCase.getRecoveryScore();

        /*
         * A recovery score is required for automated recovery.
         * If the score is missing, do not automatically retry or
         * change the payment method.
         */
        if (score == null) {
            return RecoveryDecision.builder()
                    .strategy(RecoveryStrategy.MANUAL_REVIEW)
                    .priority(RecoveryPriority.LOW)
                    .recoveryScore(null)
                    .reason("Recovery score is missing. Manual review is required.")
                    .build();
        }

        RecoveryStrategy strategy = determineStrategy(errorCode);

        RecoveryPriority priority = determinePriority(score);

        String reason = buildReason(
                errorCode,
                score,
                strategy
        );

        log.info(
                "Recovery strategy determined. paymentId={}, strategy={}, priority={}, score={}",
                payment.getExternalPaymentId(),
                strategy,
                priority,
                score
        );

        return RecoveryDecision.builder()
                .strategy(strategy)
                .priority(priority)
                .recoveryScore(score)
                .reason(reason)
                .build();
    }

    private RecoveryStrategy determineStrategy(String errorCode) {

        if (errorCode == null || errorCode.isBlank()) {
            return RecoveryStrategy.MANUAL_REVIEW;
        }

        return switch (errorCode) {

            case "INSUFFICIENT_FUNDS" ->
                    RecoveryStrategy.RETRY_PAYMENT;

            case "CARD_EXPIRED" ->
                    RecoveryStrategy.UPDATE_PAYMENT_METHOD;

            case "CARD_DECLINED" ->
                    RecoveryStrategy.RETRY_PAYMENT;

            case "AUTHENTICATION_FAILED" ->
                    RecoveryStrategy.CUSTOMER_ACTION_REQUIRED;

            default ->
                    RecoveryStrategy.MANUAL_REVIEW;
        };
    }

    private RecoveryPriority determinePriority(BigDecimal score) {

        if (score == null) {
            return RecoveryPriority.LOW;
        }

        if (score.compareTo(new BigDecimal("0.80")) >= 0) {
            return RecoveryPriority.HIGH;
        }

        if (score.compareTo(new BigDecimal("0.60")) >= 0) {
            return RecoveryPriority.MEDIUM_HIGH;
        }

        if (score.compareTo(new BigDecimal("0.40")) >= 0) {
            return RecoveryPriority.MEDIUM;
        }

        return RecoveryPriority.LOW;
    }

    private String buildReason(
            String errorCode,
            BigDecimal score,
            RecoveryStrategy strategy
    ) {

        return String.format(
                "Payment failed with error '%s'. Recovery score is %s. Recommended strategy is %s.",
                errorCode,
                score,
                strategy
        );
    }
}