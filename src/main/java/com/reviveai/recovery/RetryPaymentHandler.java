package com.reviveai.recovery;

import com.reviveai.entity.RecoveryCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class RetryPaymentHandler implements RecoveryActionHandler {

    @Override
    public RecoveryStrategy getStrategy() {
        return RecoveryStrategy.RETRY_PAYMENT;
    }

    @Override
    public RecoveryOutcome handle(
            RecoveryCase recoveryCase,
            RecoveryDecision decision
    ) {

        // ---------------------------------------------------------
        // 1. Validate recovery case
        // ---------------------------------------------------------

        if (recoveryCase == null) {
            throw new IllegalArgumentException(
                    "Recovery case cannot be null"
            );
        }

        // ---------------------------------------------------------
        // 2. Validate recovery decision
        // ---------------------------------------------------------

        if (decision == null) {
            throw new IllegalArgumentException(
                    "Recovery decision cannot be null"
            );
        }

        // ---------------------------------------------------------
        // 3. Validate strategy
        // ---------------------------------------------------------

        if (decision.getStrategy() != RecoveryStrategy.RETRY_PAYMENT) {

            throw new IllegalArgumentException(
                    "Invalid strategy for RetryPaymentHandler: "
                            + decision.getStrategy()
            );
        }

        // ---------------------------------------------------------
        // 4. Log retry request
        // ---------------------------------------------------------

        log.info(
                "Retry payment handler invoked. " +
                        "recoveryCaseId={}, strategy={}, score={}, priority={}",
                recoveryCase.getId(),
                decision.getStrategy(),
                decision.getRecoveryScore(),
                decision.getPriority()
        );

        // ---------------------------------------------------------
        // 5. Validate failed payment
        // ---------------------------------------------------------

        if (recoveryCase.getFailedPayment() == null) {

            throw new IllegalStateException(
                    "Recovery case does not contain a failed payment. " +
                            "recoveryCaseId=" + recoveryCase.getId()
            );
        }

        String paymentId =
                recoveryCase
                        .getFailedPayment()
                        .getExternalPaymentId();

        // ---------------------------------------------------------
        // 6. Validate external payment ID
        // ---------------------------------------------------------

        if (paymentId == null || paymentId.isBlank()) {

            throw new IllegalStateException(
                    "Failed payment does not contain an external payment ID. " +
                            "recoveryCaseId=" + recoveryCase.getId()
            );
        }

        // ---------------------------------------------------------
        // 7. Execution boundary
        // ---------------------------------------------------------

        /*
         * Actual Razorpay payment retry will be integrated here.
         *
         * Current flow:
         *
         * Payment Failure
         *       ↓
         * Recovery Case
         *       ↓
         * ML Recovery Score
         *       ↓
         * Recovery Strategy
         *       ↓
         * Safety Guard
         *       ↓
         * RetryPaymentHandler
         *
         * Future flow:
         *
         * RetryPaymentHandler
         *       ↓
         * Razorpay Payment API
         *       ↓
         * Retry Result
         *       ↓
         * RecoveryAction / RecoveryCase update
         */

        log.info(
                "Payment retry execution boundary reached. " +
                        "paymentId={}, recoveryCaseId={}, " +
                        "score={}, priority={}",
                paymentId,
                recoveryCase.getId(),
                decision.getRecoveryScore(),
                decision.getPriority()
        );

        // ---------------------------------------------------------
        // 8. Current implementation
        // ---------------------------------------------------------

        /*
         * IMPORTANT:
         *
         * Razorpay retry is not integrated yet.
         *
         * Therefore we must NOT report the payment as RECOVERED.
         *
         * The recovery action itself was successfully dispatched,
         * but the actual payment result is still unknown.
         *
         * The RecoveryCase should remain IN_PROGRESS until a
         * payment-success or payment-failure event is received.
         */

        log.info(
                "Retry payment handler completed. " +
                        "Payment provider integration pending. " +
                        "paymentId={}, recoveryCaseId={}",
                paymentId,
                recoveryCase.getId()
        );

        return new RecoveryOutcome(
                RecoveryOutcome.OutcomeStatus.SUBMITTED,
                BigDecimal.ZERO,
                "Payment retry request has not yet been integrated " +
                        "with Razorpay. Execution boundary reached; " +
                        "awaiting payment provider integration."
        );
    }
}