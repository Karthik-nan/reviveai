package com.reviveai.recovery;

import com.reviveai.entity.RecoveryCase;

/**
 * Contract for executing a recovery strategy.
 *
 * Each recovery strategy must have its own handler implementation.
 *
 * Examples:
 * - RETRY_PAYMENT -> RetryPaymentHandler
 * - UPDATE_PAYMENT_METHOD -> UpdatePaymentMethodHandler
 * - CUSTOMER_ACTION_REQUIRED -> CustomerActionRequiredHandler
 * - MANUAL_REVIEW -> ManualReviewHandler
 */
public interface RecoveryActionHandler {

    /**
     * Returns the recovery strategy handled by this implementation.
     *
     * @return supported recovery strategy
     */
    RecoveryStrategy getStrategy();

    /**
     * Executes the recovery action.
     *
     * @param recoveryCase recovery case being processed
     * @param decision     approved recovery decision
     */
    void handle(
            RecoveryCase recoveryCase,
            RecoveryDecision decision
    );
}