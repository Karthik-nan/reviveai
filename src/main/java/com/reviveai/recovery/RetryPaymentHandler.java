package com.reviveai.recovery;

import com.reviveai.entity.RecoveryCase;
import com.reviveai.payment.RazorpayPaymentService;
import com.reviveai.payment.RazorpayRetryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class RetryPaymentHandler implements RecoveryActionHandler {

    private final RazorpayPaymentService razorpayPaymentService;

    public RetryPaymentHandler(
            RazorpayPaymentService razorpayPaymentService
    ) {
        this.razorpayPaymentService =
                razorpayPaymentService;
    }

    // =========================================================
    // STRATEGY
    // =========================================================

    @Override
    public RecoveryStrategy getStrategy() {

        return RecoveryStrategy.RETRY_PAYMENT;
    }

    // =========================================================
    // HANDLE RETRY PAYMENT
    // =========================================================

    @Override
    public RecoveryOutcome handle(
            RecoveryCase recoveryCase,
            RecoveryDecision decision
    ) {

        // =====================================================
        // 1. VALIDATE RECOVERY CASE
        // =====================================================

        if (recoveryCase == null) {

            throw new IllegalArgumentException(
                    "Recovery case cannot be null"
            );
        }

        // =====================================================
        // 2. VALIDATE RECOVERY DECISION
        // =====================================================

        if (decision == null) {

            throw new IllegalArgumentException(
                    "Recovery decision cannot be null"
            );
        }

        // =====================================================
        // 3. VALIDATE STRATEGY
        // =====================================================

        if (decision.getStrategy()
                != RecoveryStrategy.RETRY_PAYMENT) {

            throw new IllegalArgumentException(
                    "Invalid strategy for RetryPaymentHandler: "
                            + decision.getStrategy()
            );
        }

        // =====================================================
        // 4. VALIDATE FAILED PAYMENT
        // =====================================================

        if (recoveryCase.getFailedPayment() == null) {

            throw new IllegalStateException(
                    "Recovery case does not contain a failed payment. "
                            + "recoveryCaseId="
                            + recoveryCase.getId()
            );
        }

        // =====================================================
        // 5. EXTRACT PAYMENT ATTEMPT
        // =====================================================

        var paymentAttempt =
                recoveryCase.getFailedPayment();

        // =====================================================
        // 6. VALIDATE EXTERNAL PAYMENT ID
        // =====================================================

        String paymentId =
                paymentAttempt.getExternalPaymentId();

        if (paymentId == null ||
                paymentId.isBlank()) {

            throw new IllegalStateException(
                    "Failed payment does not contain an external payment ID. "
                            + "recoveryCaseId="
                            + recoveryCase.getId()
            );
        }

        // =====================================================
        // 7. LOG RETRY REQUEST
        // =====================================================

        log.info(
                "Retry payment handler invoked. "
                        + "recoveryCaseId={}, paymentId={}, "
                        + "strategy={}, score={}, priority={}",
                recoveryCase.getId(),
                paymentId,
                decision.getStrategy(),
                decision.getRecoveryScore(),
                decision.getPriority()
        );

        // =====================================================
        // 8. MARK RECOVERY CASE IN PROGRESS
        // =====================================================

        recoveryCase.setStatus(
                RecoveryCase.RecoveryStatus.IN_PROGRESS
        );

        log.info(
                "Recovery case marked IN_PROGRESS. "
                        + "recoveryCaseId={}, paymentId={}",
                recoveryCase.getId(),
                paymentId
        );

        // =====================================================
        // 9. CALL RAZORPAY PAYMENT SERVICE
        // =====================================================

        RazorpayRetryResult retryResult;

        try {

            retryResult =
                    razorpayPaymentService.retryPayment(
                            paymentAttempt
                    );

        } catch (Exception e) {

            log.error(
                    "Razorpay payment retry failed unexpectedly. "
                            + "recoveryCaseId={}, paymentId={}",
                    recoveryCase.getId(),
                    paymentId,
                    e
            );

            return new RecoveryOutcome(
                    RecoveryOutcome.OutcomeStatus.FAILED,
                    BigDecimal.ZERO,
                    "Razorpay payment retry failed: "
                            + e.getMessage()
            );
        }

        // =====================================================
        // 10. VALIDATE PROVIDER RESULT
        // =====================================================

        if (retryResult == null) {

            throw new IllegalStateException(
                    "Razorpay payment service returned null result. "
                            + "recoveryCaseId="
                            + recoveryCase.getId()
            );
        }

        // =====================================================
        // 11. LOG PROVIDER RESULT
        // =====================================================

        log.info(
                "Razorpay retry result received. "
                        + "recoveryCaseId={}, paymentId={}, "
                        + "status={}, providerPaymentId={}, message={}",
                recoveryCase.getId(),
                paymentId,
                retryResult.getStatus(),
                retryResult.getPaymentId(),
                retryResult.getMessage()
        );

        // =====================================================
        // 12. HANDLE FAILED SUBMISSION
        // =====================================================

        if (retryResult.getStatus()
                == RazorpayRetryResult.RetryStatus.FAILED) {

            log.warn(
                    "Razorpay retry submission failed. "
                            + "recoveryCaseId={}, paymentId={}, "
                            + "message={}",
                    recoveryCase.getId(),
                    paymentId,
                    retryResult.getMessage()
            );

            return new RecoveryOutcome(
                    RecoveryOutcome.OutcomeStatus.FAILED,
                    BigDecimal.ZERO,
                    retryResult.getMessage()
            );
        }

        // =====================================================
        // 13. HANDLE SUBMITTED RETRY
        // =====================================================

        if (retryResult.getStatus()
                == RazorpayRetryResult.RetryStatus.SUBMITTED) {

            /*
             * IMPORTANT:
             *
             * SUBMITTED does NOT mean RECOVERED.
             *
             * Razorpay's final result must be received through
             * the webhook.
             *
             * Therefore we intentionally do NOT:
             *
             * - mark PaymentAttempt SUCCESS
             * - mark RecoveryCase RECOVERED
             * - set amountRecovered
             *
             * The webhook will perform those state transitions.
             */

            log.info(
                    "Razorpay retry submitted successfully. "
                            + "Awaiting payment webhook. "
                            + "recoveryCaseId={}, paymentId={}",
                    recoveryCase.getId(),
                    paymentId
            );

            return new RecoveryOutcome(
                    RecoveryOutcome.OutcomeStatus.SUBMITTED,
                    BigDecimal.ZERO,
                    retryResult.getMessage()
            );
        }

        // =====================================================
        // 14. DEFENSIVE VALIDATION
        // =====================================================

        throw new IllegalStateException(
                "Unsupported Razorpay retry status: "
                        + retryResult.getStatus()
        );
    }
}
