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

import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void shouldProcessPaymentFailure() {

        PaymentFailedEvent.Payment payment =
                new PaymentFailedEvent.Payment();

        payment.setId("pay_test_001");
        payment.setSubscriptionId("sub_test_001");
        payment.setAmount(7000L);
        payment.setErrorCode("INSUFFICIENT_FUNDS");
        payment.setErrorDescription("Insufficient funds");

        PaymentFailedEvent.Payload payload =
                new PaymentFailedEvent.Payload();

        payload.setPayment(payment);

        PaymentFailedEvent event =
                new PaymentFailedEvent();

        event.setPayload(payload);

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

    @Test
    void shouldIgnoreDuplicatePayment() {

        PaymentFailedEvent.Payment payment =
                new PaymentFailedEvent.Payment();

        payment.setId("pay_duplicate");
        payment.setSubscriptionId("sub_test_001");

        PaymentFailedEvent.Payload payload =
                new PaymentFailedEvent.Payload();

        payload.setPayment(payment);

        PaymentFailedEvent event =
                new PaymentFailedEvent();

        event.setPayload(payload);

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

    @Test
    void shouldIgnorePaymentWhenPaymentIdIsMissing() {

        PaymentFailedEvent.Payment payment =
                new PaymentFailedEvent.Payment();

        payment.setSubscriptionId("sub_test_001");

        PaymentFailedEvent.Payload payload =
                new PaymentFailedEvent.Payload();

        payload.setPayment(payment);

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

    @Test
    void shouldIgnorePaymentWhenSubscriptionIdIsMissing() {

        PaymentFailedEvent.Payment payment =
                new PaymentFailedEvent.Payment();

        payment.setId("pay_test_001");

        PaymentFailedEvent.Payload payload =
                new PaymentFailedEvent.Payload();

        payload.setPayment(payment);

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

    @Test
    void shouldThrowExceptionWhenSubscriptionDoesNotExist() {

        PaymentFailedEvent.Payment payment =
                new PaymentFailedEvent.Payment();

        payment.setId("pay_test_002");
        payment.setSubscriptionId("sub_missing");
        payment.setAmount(7000L);
        payment.setErrorCode("CARD_DECLINED");

        PaymentFailedEvent.Payload payload =
                new PaymentFailedEvent.Payload();

        payload.setPayment(payment);

        PaymentFailedEvent event =
                new PaymentFailedEvent();

        event.setPayload(payload);

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

        assertThrows(
                IllegalStateException.class,
                () ->
                        paymentRecoveryService
                                .processPaymentFailure(event)
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

