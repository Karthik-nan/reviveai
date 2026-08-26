package com.reviveai.service;

import com.reviveai.dto.PaymentFailedEvent;
import com.reviveai.entity.PaymentAttempt;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.entity.Subscription;
import com.reviveai.recovery.RecoveryActionExecutor;
import com.reviveai.recovery.RecoveryDecision;
import com.reviveai.recovery.RecoveryPriority;
import com.reviveai.recovery.RecoveryStrategy;
import com.reviveai.recovery.RecoveryStrategyEngine;
import com.reviveai.repository.PaymentAttemptRepository;
import com.reviveai.repository.RecoveryCaseRepository;
import com.reviveai.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentRecoveryServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private RecoveryCaseRepository recoveryCaseRepository;

    @Mock
    private RecoveryStrategyEngine recoveryStrategyEngine;

    @Mock
    private RecoveryActionExecutor recoveryActionExecutor;

    @InjectMocks
    private PaymentRecoveryService paymentRecoveryService;


    // ============================================================
    // Helper method
    // ============================================================

    private PaymentFailedEvent.Payment createPayment(
            String paymentId,
            String subscriptionId,
            Long amount,
            String errorCode,
            String errorDescription
    ) {

        PaymentFailedEvent.Entity entity =
                new PaymentFailedEvent.Entity();

        entity.setId(paymentId);
        entity.setSubscriptionId(subscriptionId);
        entity.setAmount(amount);
        entity.setErrorCode(errorCode);
        entity.setErrorDescription(errorDescription);

        PaymentFailedEvent.Payment payment =
                new PaymentFailedEvent.Payment();

        payment.setEntity(entity);

        return payment;
    }


    private PaymentFailedEvent createEvent(
            PaymentFailedEvent.Payment payment
    ) {

        PaymentFailedEvent.Payload payload =
                new PaymentFailedEvent.Payload();

        payload.setPayment(payment);

        PaymentFailedEvent event =
                new PaymentFailedEvent();

        event.setPayload(payload);

        return event;
    }


    // ============================================================
    // 1. Successful payment failure processing
    // ============================================================

    @Test
    void shouldProcessPaymentFailure() {

        PaymentFailedEvent.Payment payment =
                createPayment(
                        "pay_test_001",
                        "sub_test_001",
                        7000L,
                        "INSUFFICIENT_FUNDS",
                        "Insufficient funds"
                );

        PaymentFailedEvent event =
                createEvent(payment);

        UUID subscriptionId = UUID.randomUUID();
        UUID paymentAttemptId = UUID.randomUUID();
        UUID recoveryCaseId = UUID.randomUUID();

        Subscription subscription =
                Subscription.builder()
                        .id(subscriptionId)
                        .externalSubscriptionId("sub_test_001")
                        .build();

        PaymentAttempt savedPaymentAttempt =
                PaymentAttempt.builder()
                        .id(paymentAttemptId)
                        .subscription(subscription)
                        .externalPaymentId("pay_test_001")
                        .idempotencyKey("pay_test_001")
                        .amount(new BigDecimal("70.00"))
                        .status(
                                PaymentAttempt.PaymentStatus.FAILED
                        )
                        .gatewayErrorCode(
                                "INSUFFICIENT_FUNDS"
                        )
                        .gatewayErrorMessage(
                                "Insufficient funds"
                        )
                        .build();

        RecoveryCase savedRecoveryCase =
                RecoveryCase.builder()
                        .id(recoveryCaseId)
                        .subscription(subscription)
                        .failedPayment(savedPaymentAttempt)
                        .status(
                                RecoveryCase.RecoveryStatus.OPEN
                        )
                        .recoveryScore(
                                new BigDecimal("0.70")
                        )
                        .amountAtRisk(
                                new BigDecimal("70.00")
                        )
                        .amountRecovered(
                                BigDecimal.ZERO
                        )
                        .build();

        RecoveryDecision recoveryDecision =
                RecoveryDecision.builder()
                        .strategy(
                                RecoveryStrategy.RETRY_PAYMENT
                        )
                        .priority(
                                RecoveryPriority.MEDIUM_HIGH
                        )
                        .recoveryScore(
                                new BigDecimal("0.70")
                        )
                        .reason(
                                "Payment failed with insufficient funds"
                        )
                        .build();

        when(
                paymentAttemptRepository
                        .findByIdempotencyKey("pay_test_001")
        ).thenReturn(Optional.empty());

        when(
                subscriptionRepository
                        .findByExternalSubscriptionId(
                                "sub_test_001"
                        )
        ).thenReturn(Optional.of(subscription));

        when(
                paymentAttemptRepository.save(
                        any(PaymentAttempt.class)
                )
        ).thenReturn(savedPaymentAttempt);

        when(
                recoveryCaseRepository.save(
                        any(RecoveryCase.class)
                )
        ).thenReturn(savedRecoveryCase);

        when(
                recoveryStrategyEngine.determineStrategy(
                        any(RecoveryCase.class)
                )
        ).thenReturn(recoveryDecision);

        paymentRecoveryService
                .processPaymentFailure(event);

        verify(
                paymentAttemptRepository,
                times(1)
        ).save(any(PaymentAttempt.class));

        verify(
                subscriptionRepository,
                times(1)
        ).save(subscription);

        verify(
                recoveryCaseRepository,
                times(1)
        ).save(any(RecoveryCase.class));

        verify(
                recoveryStrategyEngine,
                times(1)
        ).determineStrategy(
                any(RecoveryCase.class)
        );

        verify(
                recoveryActionExecutor,
                times(1)
        ).execute(
                any(RecoveryCase.class),
                eq(recoveryDecision)
        );
    }


    // ============================================================
    // 2. Duplicate payment
    // ============================================================

    @Test
    void shouldIgnoreDuplicatePayment() {

        PaymentFailedEvent.Payment payment =
                createPayment(
                        "pay_duplicate",
                        "sub_test_001",
                        7000L,
                        "INSUFFICIENT_FUNDS",
                        "Insufficient funds"
                );

        PaymentFailedEvent event =
                createEvent(payment);

        PaymentAttempt existingPayment =
                PaymentAttempt.builder()
                        .id(UUID.randomUUID())
                        .externalPaymentId("pay_duplicate")
                        .idempotencyKey("pay_duplicate")
                        .build();

        when(
                paymentAttemptRepository
                        .findByIdempotencyKey(
                                "pay_duplicate"
                        )
        ).thenReturn(
                Optional.of(existingPayment)
        );

        paymentRecoveryService
                .processPaymentFailure(event);

        verify(
                paymentAttemptRepository,
                never()
        ).save(any(PaymentAttempt.class));

        verify(
                recoveryCaseRepository,
                never()
        ).save(any(RecoveryCase.class));

        verify(
                subscriptionRepository,
                never()
        ).save(any(Subscription.class));

        verify(
                recoveryStrategyEngine,
                never()
        ).determineStrategy(
                any(RecoveryCase.class)
        );

        verify(
                recoveryActionExecutor,
                never()
        ).execute(
                any(RecoveryCase.class),
                any(RecoveryDecision.class)
        );
    }


    // ============================================================
    // 3. Null event
    // ============================================================

    @Test
    void shouldIgnoreInvalidEvent() {

        paymentRecoveryService
                .processPaymentFailure(null);

        verifyNoInteractions(
                paymentAttemptRepository,
                subscriptionRepository,
                recoveryCaseRepository,
                recoveryStrategyEngine,
                recoveryActionExecutor
        );
    }


    // ============================================================
    // 4. Missing payload
    // ============================================================

    @Test
    void shouldIgnoreEventWhenPayloadIsMissing() {

        PaymentFailedEvent event =
                new PaymentFailedEvent();

        paymentRecoveryService
                .processPaymentFailure(event);

        verifyNoInteractions(
                paymentAttemptRepository,
                subscriptionRepository,
                recoveryCaseRepository,
                recoveryStrategyEngine,
                recoveryActionExecutor
        );
    }


    // ============================================================
    // 5. Missing payment
    // ============================================================

    @Test
    void shouldIgnoreEventWhenPaymentIsMissing() {

        PaymentFailedEvent.Payload payload =
                new PaymentFailedEvent.Payload();

        PaymentFailedEvent event =
                new PaymentFailedEvent();

        event.setPayload(payload);

        paymentRecoveryService
                .processPaymentFailure(event);

        verifyNoInteractions(
                paymentAttemptRepository,
                subscriptionRepository,
                recoveryCaseRepository,
                recoveryStrategyEngine,
                recoveryActionExecutor
        );
    }


    // ============================================================
    // 6. Missing payment ID
    // ============================================================

    @Test
    void shouldIgnorePaymentWhenPaymentIdIsMissing() {

        PaymentFailedEvent.Entity entity =
                new PaymentFailedEvent.Entity();

        entity.setSubscriptionId("sub_test_001");

        PaymentFailedEvent.Payment payment =
                new PaymentFailedEvent.Payment();

        payment.setEntity(entity);

        PaymentFailedEvent event =
                createEvent(payment);

        paymentRecoveryService
                .processPaymentFailure(event);

        verifyNoInteractions(
                paymentAttemptRepository,
                subscriptionRepository,
                recoveryCaseRepository,
                recoveryStrategyEngine,
                recoveryActionExecutor
        );
    }


    // ============================================================
    // 7. Missing subscription ID
    // ============================================================

    @Test
    void shouldIgnorePaymentWhenSubscriptionIdIsMissing() {

        PaymentFailedEvent.Entity entity =
                new PaymentFailedEvent.Entity();

        entity.setId("pay_test_001");

        PaymentFailedEvent.Payment payment =
                new PaymentFailedEvent.Payment();

        payment.setEntity(entity);

        PaymentFailedEvent event =
                createEvent(payment);

        paymentRecoveryService
                .processPaymentFailure(event);

        verifyNoInteractions(
                paymentAttemptRepository,
                subscriptionRepository,
                recoveryCaseRepository,
                recoveryStrategyEngine,
                recoveryActionExecutor
        );
    }


    // ============================================================
    // 8. Subscription does not exist
    // ============================================================

    @Test
    void shouldIgnorePaymentWhenSubscriptionDoesNotExist() {

        PaymentFailedEvent.Payment payment =
                createPayment(
                        "pay_test_002",
                        "sub_missing",
                        7000L,
                        "CARD_DECLINED",
                        "Card declined"
                );

        PaymentFailedEvent event =
                createEvent(payment);

        when(
                paymentAttemptRepository
                        .findByIdempotencyKey(
                                "pay_test_002"
                        )
        ).thenReturn(Optional.empty());

        when(
                subscriptionRepository
                        .findByExternalSubscriptionId(
                                "sub_missing"
                        )
        ).thenReturn(Optional.empty());

        paymentRecoveryService
                .processPaymentFailure(event);

        verify(
                subscriptionRepository,
                times(1)
        ).findByExternalSubscriptionId(
                "sub_missing"
        );

        verify(
                paymentAttemptRepository,
                never()
        ).save(any(PaymentAttempt.class));

        verify(
                recoveryCaseRepository,
                never()
        ).save(any(RecoveryCase.class));

        verify(
                recoveryStrategyEngine,
                never()
        ).determineStrategy(
                any(RecoveryCase.class)
        );

        verify(
                recoveryActionExecutor,
                never()
        ).execute(
                any(RecoveryCase.class),
                any(RecoveryDecision.class)
        );
    }
}
