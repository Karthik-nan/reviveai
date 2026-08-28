package com.reviveai.ml;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class RecoveryPredictionResponse {

    private BigDecimal recoveryProbability;

    private String modelVersion;

    private String predictionReason;
}