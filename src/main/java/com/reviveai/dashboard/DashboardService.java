package com.reviveai.dashboard;

import com.reviveai.dashboard.dto.DashboardOverviewResponse;

import java.util.UUID;

public interface DashboardService {

    DashboardOverviewResponse getOverview();

    void runRecoveryAnalysis();

    void simulateSuccessfulPayment(UUID recoveryCaseId);

}
