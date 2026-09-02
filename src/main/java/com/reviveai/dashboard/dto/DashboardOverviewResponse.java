package com.reviveai.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

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

    // =========================================================
    // RECOVERY TREND
    // =========================================================

    private List<com.reviveai.dashboard.dto.RecoveryTrendPoint> recoveryTrend;
}