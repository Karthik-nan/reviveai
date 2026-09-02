package com.reviveai.dashboard;

import com.reviveai.dashboard.dto.DashboardOverviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public DashboardOverviewResponse getOverview() {
        return dashboardService.getOverview();
    }

    @PostMapping("/recovery-analysis")
    public void runRecoveryAnalysis() {
        dashboardService.runRecoveryAnalysis();
    }
}
