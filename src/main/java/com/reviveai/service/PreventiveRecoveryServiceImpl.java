package com.reviveai.service;

import com.reviveai.entity.RecoveryAction;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.entity.SubscriptionHealth;
import com.reviveai.recovery.RecoveryPriority;
import com.reviveai.recovery.RecoveryStrategy;
import com.reviveai.repository.RecoveryActionRepository;
import com.reviveai.repository.RecoveryCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreventiveRecoveryServiceImpl
        implements PreventiveRecoveryService {

    private static final BigDecimal HEALTH_SCORE_THRESHOLD =
            new BigDecimal("0.60");

    private static final int CONSECUTIVE_FAILURE_THRESHOLD = 2;

    private static final int RECENT_FAILURE_THRESHOLD = 3;

    private final RecoveryCaseRepository recoveryCaseRepository;

    private final RecoveryActionRepository recoveryActionRepository;

    @Override
    public boolean shouldTriggerPreventiveRecovery(
            SubscriptionHealth health
    ) {

        if (health == null) {

            log.warn(
                    "Preventive recovery evaluation skipped. " +
                            "SubscriptionHealth is null."
            );

            return false;
        }

        if (Boolean.TRUE.equals(
                health.getPreventiveActionTriggered()
        )) {

            log.info(
                    "Preventive recovery already triggered. subscriptionId={}",
                    health.getSubscription().getId()
            );

            return false;
        }

        BigDecimal healthScore =
                health.getHealthScore();

        boolean unhealthy =
                healthScore != null &&
                        healthScore.compareTo(
                                HEALTH_SCORE_THRESHOLD
                        ) < 0;

        boolean elevatedRisk =
                health.getRiskLevel()
                        == SubscriptionHealth.RiskLevel.MEDIUM
                        || health.getRiskLevel()
                        == SubscriptionHealth.RiskLevel.HIGH
                        || health.getRiskLevel()
                        == SubscriptionHealth.RiskLevel.CRITICAL;

        boolean repeatedFailures =
                health.getConsecutiveFailures() != null
                        && health.getConsecutiveFailures()
                        >= CONSECUTIVE_FAILURE_THRESHOLD;

        boolean recentFailures =
                health.getRecentFailureCount() != null
                        && health.getRecentFailureCount()
                        >= RECENT_FAILURE_THRESHOLD;

        boolean behaviorDeclining =
                Boolean.TRUE.equals(
                        health.getPaymentBehaviorDeclining()
                );

        boolean trigger =
                unhealthy
                        || elevatedRisk
                        || repeatedFailures
                        || recentFailures
                        || behaviorDeclining;

        log.info(
                "Preventive recovery evaluation. " +
                        "subscriptionId={}, " +
                        "healthScore={}, " +
                        "riskLevel={}, " +
                        "consecutiveFailures={}, " +
                        "recentFailures={}, " +
                        "behaviorDeclining={}, " +
                        "trigger={}",
                health.getSubscription().getId(),
                healthScore,
                health.getRiskLevel(),
                health.getConsecutiveFailures(),
                health.getRecentFailureCount(),
                behaviorDeclining,
                trigger
        );

        return trigger;
    }

    @Override
    @Transactional
    public RecoveryAction createPreventiveRecoveryAction(
            SubscriptionHealth health
    ) {

        if (health == null ||
                health.getSubscription() == null ||
                health.getSubscription().getId() == null) {

            throw new IllegalArgumentException(
                    "SubscriptionHealth and subscription cannot be null"
            );
        }

        /*
         * ============================================================
         * 1. CHECK WHETHER PREVENTIVE RECOVERY SHOULD RUN
         * ============================================================
         */

        if (!shouldTriggerPreventiveRecovery(health)) {

            log.info(
                    "Preventive recovery action not created. " +
                            "Preventive recovery conditions not met. " +
                            "subscriptionId={}",
                    health.getSubscription().getId()
            );

            return null;
        }

        /*
         * ============================================================
         * 2. FIND ACTIVE RECOVERY CASE
         * ============================================================
         */

        List<RecoveryCase> recoveryCases =
                recoveryCaseRepository
                        .findBySubscriptionId(
                                health.getSubscription().getId()
                        );

        RecoveryCase activeRecoveryCase =
                recoveryCases.stream()
                        .filter(this::isActiveRecoveryCase)
                        .findFirst()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "No active RecoveryCase found for subscription: "
                                                + health.getSubscription().getId()
                                )
                        );

        /*
         * ============================================================
         * 3. DETERMINE PREVENTIVE STRATEGY
         * ============================================================
         */

        RecoveryStrategy strategy =
                determineStrategy(health);

        /*
         * ============================================================
         * 4. DETERMINE PRIORITY
         * ============================================================
         */

        RecoveryPriority priority =
                determinePriority(health);

        /*
         * ============================================================
         * 5. PREVENT DUPLICATE STRATEGY
         * ============================================================
         */

        boolean actionAlreadyExists =
                recoveryActionRepository
                        .findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                                activeRecoveryCase.getId(),
                                strategy
                        )
                        .isPresent();

        if (actionAlreadyExists) {

            log.info(
                    "Preventive recovery action already exists. " +
                            "recoveryCaseId={}, strategy={}",
                    activeRecoveryCase.getId(),
                    strategy
            );

            health.setPreventiveActionTriggered(true);

            return recoveryActionRepository
                    .findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                            activeRecoveryCase.getId(),
                            strategy
                    )
                    .orElseThrow();
        }

        /*
         * ============================================================
         * 6. BUILD RECOVERY ACTION
         * ============================================================
         */

        String reason =
                buildReason(health);

        RecoveryAction action =
                RecoveryAction.builder()
                        .recoveryCase(activeRecoveryCase)
                        .strategy(strategy)
                        .priority(priority)
                        .recoveryScore(
                                health.getHealthScore() != null
                                        ? BigDecimal.ONE.subtract(
                                        health.getHealthScore()
                                )
                                        : BigDecimal.ZERO
                        )
                        .status(
                                RecoveryAction.ActionStatus.PENDING
                        )
                        .reason(reason)
                        .build();

        /*
         * ============================================================
         * 7. SAVE ACTION
         * ============================================================
         */

        RecoveryAction savedAction =
                recoveryActionRepository.save(action);

        /*
         * ============================================================
         * 8. MARK PREVENTIVE ACTION AS TRIGGERED
         * ============================================================
         */

        health.setPreventiveActionTriggered(true);

        /*
         * ============================================================
         * 9. LOG
         * ============================================================
         */

        log.info(
                "Preventive recovery action created. " +
                        "subscriptionId={}, " +
                        "recoveryCaseId={}, " +
                        "actionId={}, " +
                        "strategy={}, " +
                        "priority={}, " +
                        "healthScore={}",
                health.getSubscription().getId(),
                activeRecoveryCase.getId(),
                savedAction.getId(),
                strategy,
                priority,
                health.getHealthScore()
        );

        return savedAction;
    }

    // ================================================================
    // ACTIVE RECOVERY CASE
    // ================================================================

    private boolean isActiveRecoveryCase(
            RecoveryCase recoveryCase
    ) {

        return recoveryCase != null
                && recoveryCase.getStatus() != null
                && (
                recoveryCase.getStatus()
                        == RecoveryCase.RecoveryStatus.OPEN
                        ||
                        recoveryCase.getStatus()
                                == RecoveryCase.RecoveryStatus.IN_PROGRESS
        );
    }

    // ================================================================
    // STRATEGY
    // ================================================================

    private RecoveryStrategy determineStrategy(
            SubscriptionHealth health
    ) {

        if (health.getConsecutiveFailures() != null
                && health.getConsecutiveFailures() >= 3) {

            return RecoveryStrategy.UPDATE_PAYMENT_METHOD;
        }

        if (health.getRecentFailureCount() != null
                && health.getRecentFailureCount() >= 3) {

            return RecoveryStrategy.UPDATE_PAYMENT_METHOD;
        }

        if (health.getPaymentBehaviorDeclining() != null
                && health.getPaymentBehaviorDeclining()) {

            return RecoveryStrategy.UPDATE_PAYMENT_METHOD;
        }

        return RecoveryStrategy.RETRY_PAYMENT;
    }

    // ================================================================
    // PRIORITY
    // ================================================================

    private RecoveryPriority determinePriority(
            SubscriptionHealth health
    ) {

        if (health.getRiskLevel()
                == SubscriptionHealth.RiskLevel.CRITICAL) {

            return RecoveryPriority.HIGH;
        }

        if (health.getRiskLevel()
                == SubscriptionHealth.RiskLevel.HIGH) {

            return RecoveryPriority.MEDIUM_HIGH;
        }

        if (health.getRiskLevel()
                == SubscriptionHealth.RiskLevel.MEDIUM) {

            return RecoveryPriority.MEDIUM;
        }

        return RecoveryPriority.LOW;
    }

    // ================================================================
    // REASON
    // ================================================================

    private String buildReason(
            SubscriptionHealth health
    ) {

        StringBuilder reason =
                new StringBuilder(
                        "Preventive recovery triggered because "
                );

        boolean addedReason = false;

        if (health.getHealthScore() != null
                && health.getHealthScore()
                .compareTo(HEALTH_SCORE_THRESHOLD) < 0) {

            reason.append(
                    "health score is below threshold"
            );

            addedReason = true;
        }

        if (health.getConsecutiveFailures() != null
                && health.getConsecutiveFailures()
                >= CONSECUTIVE_FAILURE_THRESHOLD) {

            if (addedReason) {
                reason.append(", ");
            }

            reason.append(
                    health.getConsecutiveFailures()
                            + " consecutive payment failures"
            );

            addedReason = true;
        }

        if (health.getRecentFailureCount() != null
                && health.getRecentFailureCount()
                >= RECENT_FAILURE_THRESHOLD) {

            if (addedReason) {
                reason.append(", ");
            }

            reason.append(
                    health.getRecentFailureCount()
                            + " recent payment failures"
            );

            addedReason = true;
        }

        if (Boolean.TRUE.equals(
                health.getPaymentBehaviorDeclining()
        )) {

            if (addedReason) {
                reason.append(", ");
            }

            reason.append(
                    "payment behavior is declining"
            );

            addedReason = true;
        }

        if (!addedReason) {

            reason.append(
                    "subscription risk is elevated"
            );
        }

        return reason.toString();
    }
}