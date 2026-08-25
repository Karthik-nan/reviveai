package com.reviveai.repository;

import com.reviveai.entity.RecoveryAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecoveryActionRepository
        extends JpaRepository<RecoveryAction, Long> {

    List<RecoveryAction> findByRecoveryCaseId(
            Long recoveryCaseId
    );

    List<RecoveryAction> findByStatus(
            RecoveryAction.ActionStatus status
    );

}
