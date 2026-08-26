package com.reviveai.repository;

import com.reviveai.entity.RecoveryAction;
import com.reviveai.recovery.RecoveryStrategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecoveryActionRepository
        extends JpaRepository<RecoveryAction, Long> {

    List<RecoveryAction> findByRecoveryCaseId(
            Long recoveryCaseId
    );

    List<RecoveryAction> findByStatus(
            RecoveryAction.ActionStatus status
    );

    Optional<RecoveryAction> findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
            Long recoveryCaseId,
            RecoveryStrategy strategy
    );

}
