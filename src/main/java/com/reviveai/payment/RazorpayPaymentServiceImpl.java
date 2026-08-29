package com.reviveai.payment;

import com.reviveai.entity.PaymentAttempt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RazorpayPaymentServiceImpl
        implements RazorpayPaymentService {

    private final RazorpayConfig razorpayConfig;

    public RazorpayPaymentServiceImpl(
            RazorpayConfig razorpayConfig
    ) {
        this.razorpayConfig = razorpayConfig;
    }

    @Override
    public RazorpayRetryResult retryPayment(
            PaymentAttempt paymentAttempt
    ) {

        // =====================================================
        // 1. VALIDATE PAYMENT ATTEMPT
        // =====================================================

        if (paymentAttempt == null) {

            throw new IllegalArgumentException(
                    "Payment attempt cannot be null"
            );
        }

        // =====================================================
        // 2. VALIDATE SUBSCRIPTION
        // =====================================================

        if (paymentAttempt.getSubscription() == null) {

            throw new IllegalStateException(
                    "Payment attempt does not contain a subscription"
            );
        }

        // =====================================================
        // 3. EXTRACT PROVIDER IDENTIFIERS
        // =====================================================

        String paymentId =
                paymentAttempt.getExternalPaymentId();

        String orderId =
                paymentAttempt.getExternalOrderId();

        String subscriptionId =
                paymentAttempt
                        .getSubscription()
                        .getExternalSubscriptionId();

        // =====================================================
        // 4. LOG REQUEST
        // =====================================================

        log.info(
                "Razorpay recovery request received. " +
                        "paymentAttemptId={}, paymentId={}, " +
                        "orderId={}, subscriptionId={}",
                paymentAttempt.getId(),
                paymentId,
                orderId,
                subscriptionId
        );

        // =====================================================
        // 5. VALIDATE PAYMENT ID
        // =====================================================

        if (paymentId == null ||
                paymentId.isBlank()) {

            log.warn(
                    "Razorpay recovery cannot proceed. " +
                            "External payment ID is missing. " +
                            "paymentAttemptId={}",
                    paymentAttempt.getId()
            );

            return new RazorpayRetryResult(
                    RazorpayRetryResult.RetryStatus.FAILED,
                    null,
                    "External Razorpay payment ID is missing."
            );
        }

        // =====================================================
        // 6. VALIDATE SUBSCRIPTION ID
        // =====================================================

        if (subscriptionId == null ||
                subscriptionId.isBlank()) {

            log.warn(
                    "Razorpay recovery cannot proceed. " +
                            "External subscription ID is missing. " +
                            "paymentAttemptId={}",
                    paymentAttempt.getId()
            );

            return new RazorpayRetryResult(
                    RazorpayRetryResult.RetryStatus.FAILED,
                    paymentId,
                    "External Razorpay subscription ID is missing."
            );
        }

        // =====================================================
        // 7. VALIDATE RAZORPAY CONFIGURATION
        // =====================================================

        if (razorpayConfig.getKeyId() == null ||
                razorpayConfig.getKeyId().isBlank()) {

            log.error(
                    "Razorpay key ID is not configured."
            );

            return new RazorpayRetryResult(
                    RazorpayRetryResult.RetryStatus.FAILED,
                    paymentId,
                    "Razorpay key ID is not configured."
            );
        }

        if (razorpayConfig.getKeySecret() == null ||
                razorpayConfig.getKeySecret().isBlank()) {

            log.error(
                    "Razorpay key secret is not configured."
            );

            return new RazorpayRetryResult(
                    RazorpayRetryResult.RetryStatus.FAILED,
                    paymentId,
                    "Razorpay key secret is not configured."
            );
        }

        // =====================================================
        // 8. PROVIDER INTEGRATION BOUNDARY
        // =====================================================

        /*
         * IMPORTANT
         * -----------------------------------------------------
         *
         * A Razorpay Subscription payment cannot be retried by
         * blindly sending the previous payment ID to a generic
         * "retry payment" API.
         *
         * Razorpay handles recurring Subscription charges and
         * retries through its Subscription/Invoice lifecycle.
         *
         * Therefore this service intentionally does NOT:
         *
         *     - create a fake payment
         *     - mark the payment as SUCCESS
         *     - mark RecoveryCase as RECOVERED
         *     - invent a nonexistent retry endpoint
         *
         * The actual final payment result must be established
         * from Razorpay's webhook events.
         */

        log.info(
                "Razorpay subscription recovery integration boundary reached. " +
                        "paymentId={}, subscriptionId={}, orderId={}",
                paymentId,
                subscriptionId,
                orderId
        );

        // =====================================================
        // 9. RETURN SUBMITTED
        // =====================================================

        /*
         * This means:
         *
         *     ReviveAI accepted the recovery action
         *
         * It does NOT mean:
         *
         *     Razorpay confirmed a successful payment
         *
         * The RecoveryCase therefore remains IN_PROGRESS.
         *
         * Final state must be determined from a Razorpay webhook.
         */

        return new RazorpayRetryResult(
                RazorpayRetryResult.RetryStatus.SUBMITTED,
                paymentId,
                "Recovery action accepted by ReviveAI. " +
                        "Final Razorpay payment status must be confirmed " +
                        "through the corresponding webhook event."
        );
    }
}

