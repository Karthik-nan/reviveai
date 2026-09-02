package com.reviveai.dashboard;

import com.reviveai.dashboard.dto.DashboardOverviewResponse;
import com.reviveai.dashboard.dto.RecoveryTrendPoint;
import com.reviveai.dto.PaymentFailedEvent;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.recovery.RecoveryStrategy;
import com.reviveai.repository.RecoveryActionRepository;
import com.reviveai.repository.RecoveryCaseRepository;
import com.reviveai.service.PaymentRecoveryService;
import com.reviveai.service.RecoveryAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final RecoveryCaseRepository recoveryCaseRepository;
    private final RecoveryActionRepository recoveryActionRepository;
    private final RecoveryAnalysisService recoveryAnalysisService;
    private final PaymentRecoveryService paymentRecoveryService;


    // =========================================================
    // DASHBOARD OVERVIEW
    // =========================================================

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
                                        .date(
                                                (java.time.LocalDate) row[0]
                                        )
                                        .amountRecovered(
                                                new BigDecimal(
                                                        row[1].toString()
                                                )
                                        )
                                        .build()
                        )
                        .toList();


        // =========================================================
        // LATEST ACTIVE RECOVERY CASE
        // =========================================================

        RecoveryCase latestRecoveryCase =
                cases.stream()
                        .filter(this::isActive)
                        .max(
                                Comparator.comparing(
                                        RecoveryCase::getCreatedAt
                                )
                        )
                        .orElse(null);


        // =========================================================
        // DASHBOARD LOGGING
        // =========================================================

        log.info(
                "Dashboard overview calculated. " +
                        "revenueAtRisk={}, revenueRecovered={}, " +
                        "recoveryRate={}, activeCases={}, " +
                        "averageRecoveryProbability={}, " +
                        "highRiskSubscriptions={}, " +
                        "automatedRecoveries={}, manualReviews={}, " +
                        "recoveryTrendPoints={}, " +
                        "latestRecoveryCaseId={}",
                revenueAtRisk,
                revenueRecovered,
                recoveryRate,
                activeCases,
                averageRecoveryProbability,
                highRiskSubscriptions,
                automatedRecoveries,
                manualReviews,
                recoveryTrend.size(),
                latestRecoveryCase != null
                        ? latestRecoveryCase.getId()
                        : null
        );


        // =========================================================
        // BUILD DASHBOARD RESPONSE
        // =========================================================

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
                .latestRecoveryCaseId(
                        latestRecoveryCase != null
                                ? latestRecoveryCase.getId()
                                : null
                )
                .build();
    }


    // =========================================================
    // RUN RECOVERY ANALYSIS
    // =========================================================

    @Override
    @Transactional
    public void runRecoveryAnalysis() {

        List<RecoveryCase> openCases =
                recoveryCaseRepository.findByStatus(
                        RecoveryCase.RecoveryStatus.OPEN
                );

        log.info(
                "Starting manual recovery analysis. openCases={}",
                openCases.size()
        );

        for (RecoveryCase recoveryCase : openCases) {

            try {

                recoveryAnalysisService.analyzeRecoveryCase(
                        recoveryCase
                );

            } catch (Exception e) {

                log.error(
                        "Manual recovery analysis failed. recoveryCaseId={}",
                        recoveryCase.getId(),
                        e
                );
            }
        }

        log.info(
                "Manual recovery analysis completed. analyzedCases={}",
                openCases.size()
        );
    }


    // =========================================================
    // SIMULATE SUCCESSFUL PAYMENT
    // =========================================================

    @Override
    @Transactional
    public void simulateSuccessfulPayment(
            UUID recoveryCaseId
    ) {

        RecoveryCase recoveryCase =
                recoveryCaseRepository.findById(
                                recoveryCaseId
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Recovery case not found: "
                                                + recoveryCaseId
                                )
                        );


        log.info(
                "Simulating successful payment for recovery case. " +
                        "recoveryCaseId={}, status={}",
                recoveryCase.getId(),
                recoveryCase.getStatus()
        );


        // =====================================================
        // VALIDATE CASE STATUS
        // =====================================================

        if (recoveryCase.getStatus()
                != RecoveryCase.RecoveryStatus.IN_PROGRESS) {

            throw new IllegalStateException(
                    "Recovery case must be IN_PROGRESS. " +
                            "Current status: "
                            + recoveryCase.getStatus()
            );
        }


        // =====================================================
        // GET ORIGINAL ORDER ID
        // =====================================================

        String externalOrderId =
                recoveryCase.getFailedPayment()
                        .getExternalOrderId();

        if (externalOrderId == null
                || externalOrderId.isBlank()) {

            throw new IllegalStateException(
                    "Recovery case does not have a valid " +
                            "external order ID."
            );
        }


        // =====================================================
        // GET SUBSCRIPTION ID
        // =====================================================

        String subscriptionId =
                recoveryCase.getSubscription()
                        .getExternalSubscriptionId();

        if (subscriptionId == null
                || subscriptionId.isBlank()) {

            subscriptionId =
                    recoveryCase.getSubscription()
                            .getId()
                            .toString();
        }


        // =====================================================
        // CREATE PAYMENT.CAPTURED EVENT
        // =====================================================

        PaymentFailedEvent event =
                new PaymentFailedEvent();

        event.setEvent(
                "payment.captured"
        );

        event.setCreatedAt(
                System.currentTimeMillis() / 1000
        );


        PaymentFailedEvent.Payload payload =
                new PaymentFailedEvent.Payload();


        PaymentFailedEvent.Payment payment =
                new PaymentFailedEvent.Payment();


        PaymentFailedEvent.Entity paymentEntity =
                new PaymentFailedEvent.Entity();


        // =====================================================
        // SIMULATED PAYMENT DATA
        // =====================================================

        paymentEntity.setId(
                "sim_captured_" + recoveryCaseId
        );

        paymentEntity.setAmount(
                recoveryCase.getAmountAtRisk()
                        .movePointRight(2)
                        .longValue()
        );

        paymentEntity.setCurrency(
                "INR"
        );

        paymentEntity.setStatus(
                "captured"
        );

        paymentEntity.setOrderId(
                externalOrderId
        );

        paymentEntity.setSubscriptionId(
                subscriptionId
        );


        payment.setEntity(
                paymentEntity
        );

        payload.setPayment(
                payment
        );


        // =====================================================
        // SIMULATED SUBSCRIPTION
        // =====================================================

        PaymentFailedEvent.Subscription subscription =
                new PaymentFailedEvent.Subscription();


        PaymentFailedEvent.SubscriptionEntity subscriptionEntity =
                new PaymentFailedEvent.SubscriptionEntity();


        subscriptionEntity.setId(
                subscriptionId
        );

        subscriptionEntity.setStatus(
                "active"
        );


        subscription.setEntity(
                subscriptionEntity
        );

        payload.setSubscription(
                subscription
        );


        // =====================================================
        // ATTACH PAYLOAD
        // =====================================================

        event.setPayload(
                payload
        );


        // =====================================================
        // REUSE EXISTING RECOVERY LOGIC
        // =====================================================

        paymentRecoveryService.processPaymentSuccess(
                event
        );


        log.info(
                "Simulated successful payment processed. " +
                        "recoveryCaseId={}",
                recoveryCaseId
        );
    }


    // =========================================================
    // ACTIVE CASE
    // =========================================================

    private boolean isActive(
            RecoveryCase recoveryCase
    ) {

        return recoveryCase.getStatus()
                == RecoveryCase.RecoveryStatus.OPEN
                || recoveryCase.getStatus()
                == RecoveryCase.RecoveryStatus.IN_PROGRESS;
    }


    // =========================================================
    // HIGH RISK
    // =========================================================

    private boolean isHighRisk(
            RecoveryCase recoveryCase
    ) {

        return recoveryCase.getRecoveryPotential()
                == RecoveryCase.RecoveryPotential.HIGH;
    }


    // =========================================================
    // RECOVERY RATE
    // =========================================================

    private BigDecimal calculateRecoveryRate(
            BigDecimal recovered,
            BigDecimal atRisk
    ) {

        if (atRisk == null
                || atRisk.compareTo(
                BigDecimal.ZERO
        ) == 0) {

            return BigDecimal.ZERO;
        }

        return recovered
                .divide(
                        atRisk,
                        4,
                        RoundingMode.HALF_UP
                )
                .multiply(
                        new BigDecimal("100")
                )
                .setScale(
                        1,
                        RoundingMode.HALF_UP
                );
    }


    // =========================================================
    // AVERAGE RECOVERY PROBABILITY
    // =========================================================

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
                        BigDecimal.valueOf(
                                scores.size()
                        ),
                        4,
                        RoundingMode.HALF_UP
                )
                .multiply(
                        new BigDecimal("100")
                )
                .setScale(
                        1,
                        RoundingMode.HALF_UP
                );
    }
}