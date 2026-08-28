package com.reviveai.ml;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class RecoveryFeatures {

    /**
     * Failed payment amount in the subscription currency.
     */
    private BigDecimal paymentAmount;

    /**
     * Current subscription risk score.
     */
    private BigDecimal subscriptionRiskScore;

    /**
     * Existing Tier 1 recovery score.
     */
    private BigDecimal tier1RecoveryScore;

    /**
     * Number of previous failed payment attempts.
     */
    private int previousFailedPayments;

    /**
     * Number of previous successful payment attempts.
     */
    private int previousSuccessfulPayments;

    /**
     * Whether the customer has previously recovered
     * from a failed payment.
     */
    private boolean previousRecoverySuccess;

    /**
     * Encoded payment error category.
     *
     * Example:
     * 0 = unknown
     * 1 = insufficient funds
     * 2 = card declined
     * 3 = card expired
     * 4 = authentication failure
     */
    private int errorCategory;

    /**
     * Whether the subscription is currently past due.
     */
    private boolean subscriptionPastDue;
}