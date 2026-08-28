package com.reviveai.ml;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class RecoveryMLPrediction {

    /**
     * Probability that the failed payment can be recovered.
     * Expected range: 0.00 - 1.00.
     */
    private BigDecimal recoveryProbability;

    /**
     * Indicates whether the ML model considers
     * this case suitable for recovery.
     */
    private boolean recoverable;

    /**
     * Name/version of the model that generated
     * this prediction.
     */
    private String modelVersion;
}