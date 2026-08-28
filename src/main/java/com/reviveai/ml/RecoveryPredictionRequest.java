package com.reviveai.ml;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class RecoveryPredictionRequest {

    private BigDecimal paymentAmount;

    private int retryCount;

    private int daysPastDue;

    private int previousSuccessfulPayments;

    private int previousFailedPayments;

    private BigDecimal paymentFailureRate;

    private String recoveryPotential;

    private String errorCode;
}