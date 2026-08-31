package com.reviveai.repository;

import com.reviveai.entity.RecoveryCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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

}