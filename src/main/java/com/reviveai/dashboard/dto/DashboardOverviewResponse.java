package com.reviveai.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class DashboardOverviewResponse {

    private BigDecimal revenueAtRisk;

    private BigDecimal revenueRecovered;

    private BigDecimal recoveryRate;

    private long activeCases;

    private BigDecimal averageRecoveryProbability;

    private long highRiskSubscriptions;

    private long automatedRecoveries;

    private long manualReviews;
}
