package com.reviveai.repository;

import com.reviveai.entity.RecoveryCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecoveryCaseRepository extends JpaRepository<RecoveryCase, UUID>
{
    Optional<RecoveryCase> findByFailedPaymentId(UUID failedPaymentId);
}
