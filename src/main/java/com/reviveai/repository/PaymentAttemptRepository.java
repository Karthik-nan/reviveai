package com.reviveai.repository;

import com.reviveai.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentAttemptRepository
        extends JpaRepository<PaymentAttempt, UUID> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<PaymentAttempt> findByIdempotencyKey(
            String idempotencyKey
    );

    Optional<PaymentAttempt> findByExternalPaymentId(
            String externalPaymentId
    );

    long countBySubscriptionIdAndStatus(
            UUID subscriptionId,
            PaymentAttempt.PaymentStatus status
    );

    long countBySubscriptionIdAndStatusAndAttemptedAtBefore(
            UUID subscriptionId,
            PaymentAttempt.PaymentStatus status,
            OffsetDateTime attemptedAt
    );
}