package com.reviveai.repository;

import com.reviveai.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentAttemptRepository
        extends JpaRepository<PaymentAttempt, UUID> {

    // ============================================================
    // IDEMPOTENCY
    // ============================================================

    boolean existsByIdempotencyKey(
            String idempotencyKey
    );

    Optional<PaymentAttempt> findByIdempotencyKey(
            String idempotencyKey
    );

    // ============================================================
    // EXTERNAL PAYMENT
    // ============================================================

    Optional<PaymentAttempt> findByExternalPaymentId(
            String externalPaymentId
    );

    Optional<PaymentAttempt> findFirstBySubscriptionIdAndExternalOrderIdAndStatus(
            UUID subscriptionId,
            String externalOrderId,
            PaymentAttempt.PaymentStatus status
    );
    // ============================================================
    // PAYMENT COUNTS
    // ============================================================

    long countBySubscriptionIdAndStatus(
            UUID subscriptionId,
            PaymentAttempt.PaymentStatus status
    );

    // ============================================================
    // PAYMENT COUNTS BEFORE DATE
    // ============================================================

    long countBySubscriptionIdAndStatusAndAttemptedAtBefore(
            UUID subscriptionId,
            PaymentAttempt.PaymentStatus status,
            OffsetDateTime attemptedAt
    );

    // ============================================================
    // PAYMENT COUNTS AFTER DATE
    // ============================================================

    long countBySubscriptionIdAndStatusAndAttemptedAtAfter(
            UUID subscriptionId,
            PaymentAttempt.PaymentStatus status,
            OffsetDateTime attemptedAt
    );

    // ============================================================
    // PAYMENT HISTORY
    // ============================================================
    //
    // Used by SubscriptionHealthEvaluatorImpl to determine
    // consecutive payment failures.
    //
    // Newest payment attempt comes first.
    // ============================================================

    List<PaymentAttempt> findBySubscriptionIdOrderByAttemptedAtDesc(
            UUID subscriptionId
    );
}

