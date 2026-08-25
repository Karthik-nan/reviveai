package com.reviveai.repository;

import com.reviveai.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {
    Optional<PaymentAttempt> findByIdempotencyKey(String idempotencyKey);
    Optional<PaymentAttempt> findByExternalPaymentId( String externalPaymentId);
}
