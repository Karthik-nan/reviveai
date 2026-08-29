package com.reviveai.recovery;

import com.reviveai.entity.RecoveryCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RetryPaymentHandler implements RecoveryActionHandler {

    @Override
    public RecoveryStrategy getStrategy() {
        return RecoveryStrategy.RETRY_PAYMENT;
    }

    @Override
    public void handle(
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

        /*
         * IMPORTANT:
         * No external payment retry is performed yet.
         *
         * The RecoveryActionExecutor will mark the action
         * as EXECUTED because the handler completed successfully.
         *
         * When Razorpay integration is added, this handler should
         * only return successfully after the retry request has been
         * successfully submitted.
         */

        log.info(
                "Retry payment handler completed successfully. " +
                        "paymentId={}, recoveryCaseId={}",
                paymentId,
                recoveryCase.getId()
        );
    }
}

