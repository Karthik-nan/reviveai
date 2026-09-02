package com.reviveai.dashboard;

import com.reviveai.dashboard.dto.DashboardOverviewResponse;
import com.reviveai.dashboard.dto.RecoveryTrendPoint;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.recovery.RecoveryStrategy;
import com.reviveai.repository.RecoveryActionRepository;
import com.reviveai.repository.RecoveryCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final RecoveryCaseRepository recoveryCaseRepository;
    private final RecoveryActionRepository recoveryActionRepository;

    @Override
    public DashboardOverviewResponse getOverview() {

        List<RecoveryCase> cases =
                recoveryCaseRepository.findAll();

        BigDecimal revenueAtRisk =
                cases.stream()
                        .map(RecoveryCase::getAmountAtRisk)
                        .filter(amount -> amount != null)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        BigDecimal revenueRecovered =
                cases.stream()
                        .map(RecoveryCase::getAmountRecovered)
                        .filter(amount -> amount != null)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        BigDecimal recoveryRate =
                calculateRecoveryRate(
                        revenueRecovered,
                        revenueAtRisk
                );

        long activeCases =
                cases.stream()
                        .filter(this::isActive)
                        .count();

        BigDecimal averageRecoveryProbability =
                calculateAverageRecoveryProbability(cases);

        long highRiskSubscriptions =
                cases.stream()
                        .filter(this::isHighRisk)
                        .map(RecoveryCase::getSubscription)
                        .filter(subscription -> subscription != null)
                        .map(subscription -> subscription.getId())
                        .distinct()
                        .count();

        long manualReviews =
                recoveryActionRepository.countByStrategy(
                        RecoveryStrategy.MANUAL_REVIEW
                );

        long totalActions =
                recoveryActionRepository.count();

        long automatedRecoveries =
                Math.max(
                        0,
                        totalActions - manualReviews
                );

        // =========================================================
        // RECOVERY TREND
        // =========================================================

        OffsetDateTime from =
                OffsetDateTime.now().minusDays(6);

        List<Object[]> trendRows =
                recoveryCaseRepository.findRecoveryTrend(from);

        List<RecoveryTrendPoint> recoveryTrend =
                trendRows.stream()
                        .map(row ->
                                RecoveryTrendPoint.builder()
                                        .date((java.time.LocalDate) row[0])
                                        .amountRecovered(
                                                new BigDecimal(
                                                        row[1].toString()
                                                )
                                        )
                                        .build()
                        )
                        .toList();

        log.info(
                "Dashboard overview calculated. " +
                        "revenueAtRisk={}, revenueRecovered={}, " +
                        "recoveryRate={}, activeCases={}, " +
                        "averageRecoveryProbability={}, " +
                        "highRiskSubscriptions={}, " +
                        "automatedRecoveries={}, manualReviews={}, " +
                        "recoveryTrendPoints={}",
                revenueAtRisk,
                revenueRecovered,
                recoveryRate,
                activeCases,
                averageRecoveryProbability,
                highRiskSubscriptions,
                automatedRecoveries,
                manualReviews,
                recoveryTrend.size()
        );

        return DashboardOverviewResponse.builder()
                .revenueAtRisk(revenueAtRisk)
                .revenueRecovered(revenueRecovered)
                .recoveryRate(recoveryRate)
                .activeCases(activeCases)
                .averageRecoveryProbability(
                        averageRecoveryProbability
                )
                .highRiskSubscriptions(
                        highRiskSubscriptions
                )
                .automatedRecoveries(
                        automatedRecoveries
                )
                .manualReviews(
                        manualReviews
                )
                .recoveryTrend(recoveryTrend)
                .build();
    }

    private boolean isActive(RecoveryCase recoveryCase) {

        return recoveryCase.getStatus()
                == RecoveryCase.RecoveryStatus.OPEN
                || recoveryCase.getStatus()
                == RecoveryCase.RecoveryStatus.IN_PROGRESS;
    }

    private boolean isHighRisk(RecoveryCase recoveryCase) {

        return recoveryCase.getRecoveryPotential()
                == RecoveryCase.RecoveryPotential.HIGH;
    }

    private BigDecimal calculateRecoveryRate(
            BigDecimal recovered,
            BigDecimal atRisk
    ) {

        if (atRisk == null
                || atRisk.compareTo(BigDecimal.ZERO) == 0) {

            return BigDecimal.ZERO;
        }

        return recovered
                .divide(
                        atRisk,
                        4,
                        RoundingMode.HALF_UP
                )
                .multiply(new BigDecimal("100"))
                .setScale(
                        1,
                        RoundingMode.HALF_UP
                );
    }

    private BigDecimal calculateAverageRecoveryProbability(
            List<RecoveryCase> cases
    ) {

        List<BigDecimal> scores =
                cases.stream()
                        .map(RecoveryCase::getRecoveryScore)
                        .filter(score -> score != null)
                        .toList();

        if (scores.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal total =
                scores.stream()
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        return total
                .divide(
                        BigDecimal.valueOf(scores.size()),
                        4,
                        RoundingMode.HALF_UP
                )
                .multiply(new BigDecimal("100"))
                .setScale(
                        1,
                        RoundingMode.HALF_UP
                );
    }
}
