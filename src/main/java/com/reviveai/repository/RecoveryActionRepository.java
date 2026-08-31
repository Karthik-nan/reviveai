package com.reviveai.repository;

import com.reviveai.entity.RecoveryAction;
import com.reviveai.recovery.RecoveryStrategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecoveryActionRepository
        extends JpaRepository<RecoveryAction, Long> {

    List<RecoveryAction> findByRecoveryCaseId(
            UUID recoveryCaseId
    );

    List<RecoveryAction> findByStatus(
            RecoveryAction.ActionStatus status
    );

    // =========================================================
    // COUNT BY STRATEGY
    // =========================================================

    long countByStrategy(
            RecoveryStrategy strategy
    );

    Optional<RecoveryAction> findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
            UUID recoveryCaseId,
            RecoveryStrategy strategy
    );
}