package com.reviveai.repository;

import com.reviveai.dashboard.dto.RecoveryTrendPoint;
import com.reviveai.entity.RecoveryCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecoveryCaseRepository
        extends JpaRepository<RecoveryCase, UUID> {

    // =========================================================
    // FIND BY FAILED PAYMENT
    // =========================================================

    Optional<RecoveryCase> findByFailedPaymentId(
            UUID failedPaymentId
    );

    // =========================================================
    // FIND BY STATUS
    // =========================================================

    List<RecoveryCase> findByStatus(
            RecoveryCase.RecoveryStatus status
    );

    // =========================================================
    // FIND BY RECOVERY POTENTIAL
    // =========================================================

    List<RecoveryCase> findByRecoveryPotential(
            RecoveryCase.RecoveryPotential recoveryPotential
    );

    // =========================================================
    // FIND BY SUBSCRIPTION
    // =========================================================

    List<RecoveryCase> findBySubscriptionId(
            UUID subscriptionId
    );

    // =========================================================
    // FIND BY SUBSCRIPTION + STATUS
    // =========================================================

    List<RecoveryCase> findBySubscriptionAndStatus(
            com.reviveai.entity.Subscription subscription,
            RecoveryCase.RecoveryStatus status
    );

    // =========================================================
    // FIND BY SUBSCRIPTION ID + STATUS
    // =========================================================

    List<RecoveryCase> findBySubscriptionIdAndStatus(
            UUID subscriptionId,
            RecoveryCase.RecoveryStatus status
    );

    Optional<RecoveryCase> findFirstBySubscriptionIdAndStatusOrderByCreatedAtDesc(
            UUID subscriptionId,
            RecoveryCase.RecoveryStatus status
    );

    // =========================================================
    // DASHBOARD RECOVERY TREND
    // =========================================================

    @Query(value = """
            SELECT
                DATE(rc.resolved_at) AS recovery_date,
                COALESCE(SUM(rc.amount_recovered), 0) AS amount_recovered
            FROM recovery_cases rc
            WHERE rc.resolved_at IS NOT NULL
              AND rc.amount_recovered > 0
              AND rc.resolved_at >= :from
            GROUP BY DATE(rc.resolved_at)
            ORDER BY DATE(rc.resolved_at)
            """, nativeQuery = true)
    List<Object[]> findRecoveryTrend(
            OffsetDateTime from
    );
}