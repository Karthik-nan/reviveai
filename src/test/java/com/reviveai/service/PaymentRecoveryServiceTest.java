package com.reviveai.service;

import com.reviveai.dto.PaymentFailedEvent;
import com.reviveai.entity.PaymentAttempt;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.entity.Subscription;
import com.reviveai.ml.RecoveryFeatureMapper;
import com.reviveai.ml.RecoveryPredictionRequest;
import com.reviveai.ml.RecoveryPredictionResponse;
import com.reviveai.ml.RecoveryPredictionService;
import com.reviveai.recovery.RecoveryActionExecutor;
import com.reviveai.recovery.RecoveryDecision;
import com.reviveai.recovery.RecoveryDecisionGuard;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class PaymentRecoveryServiceTest {


    // ============================================================
    // REPOSITORIES
    // ============================================================

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private RecoveryCaseRepository recoveryCaseRepository;


    // ============================================================
    // RECOVERY ENGINE
    // ============================================================

    @Mock
    private RecoveryStrategyEngine recoveryStrategyEngine;

    @Mock
    private RecoveryDecisionGuard recoveryDecisionGuard;

    @Mock
    private RecoveryActionExecutor recoveryActionExecutor;


    // ============================================================
    // ML
    // ============================================================

    @Mock
    private RecoveryFeatureMapper recoveryFeatureMapper;

    @Mock
    private RecoveryPredictionService recoveryPredictionService;


    // ============================================================
    // SERVICE UNDER TEST
    // ============================================================

    @InjectMocks
    private PaymentRecoveryService paymentRecoveryService;


    // ============================================================
    // HELPER: CREATE PAYMENT
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


    // ============================================================
    // HELPER: CREATE EVENT
    // ============================================================

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
    // 1. SUCCESSFUL PAYMENT FAILURE PROCESSING
    // ============================================================

    @Test
    void shouldProcessPaymentFailure() {


        // ========================================================
        // Payment event
        // ========================================================

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


        // ========================================================
        // IDs
        // ========================================================

        UUID subscriptionId =
                UUID.randomUUID();

        UUID paymentAttemptId =
                UUID.randomUUID();

        UUID recoveryCaseId =
                UUID.randomUUID();


        // ========================================================
        // Subscription
        // ========================================================

        Subscription subscription =
                Subscription.builder()
                        .id(subscriptionId)
                        .externalSubscriptionId(
                                "sub_test_001"
                        )
                        .build();


        // ========================================================
        // Payment attempt
        // ========================================================

        PaymentAttempt savedPaymentAttempt =
                PaymentAttempt.builder()
                        .id(paymentAttemptId)
                        .subscription(subscription)
                        .externalPaymentId(
                                "pay_test_001"
                        )
                        .idempotencyKey(
                                "pay_test_001"
                        )
                        .amount(
                                new BigDecimal("70.00")
                        )
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


        // ========================================================
        // Recovery case
        // ========================================================

        RecoveryCase savedRecoveryCase =
                RecoveryCase.builder()
                        .id(recoveryCaseId)
                        .subscription(subscription)
                        .failedPayment(
                                savedPaymentAttempt
                        )
                        .status(
                                RecoveryCase.RecoveryStatus.OPEN
                        )
                        .recoveryScore(
                                new BigDecimal("0.70")
                        )
                        .recoveryPotential(
                                RecoveryCase.RecoveryPotential.HIGH
                        )
                        .amountAtRisk(
                                new BigDecimal("70.00")
                        )
                        .amountRecovered(
                                BigDecimal.ZERO
                        )
                        .build();


        // ========================================================
        // ML feature request
        // ========================================================

        RecoveryPredictionRequest predictionRequest =
                RecoveryPredictionRequest.builder()
                        .paymentAmount(
                                new BigDecimal("70.00")
                        )
                        .retryCount(0)
                        .daysPastDue(0)
                        .previousSuccessfulPayments(0)
                        .previousFailedPayments(0)
                        .paymentFailureRate(
                                BigDecimal.ZERO
                        )
                        .recoveryPotential(
                                "HIGH"
                        )
                        .errorCode(
                                "INSUFFICIENT_FUNDS"
                        )
                        .build();


        // ========================================================
        // ML prediction response
        // ========================================================

        RecoveryPredictionResponse predictionResponse =
                RecoveryPredictionResponse.builder()
                        .recoveryProbability(
                                new BigDecimal("0.75")
                        )
                        .modelVersion(
                                "tier2-v1"
                        )
                        .predictionReason(
                                "Tier 2 baseline prediction"
                        )
                        .build();


        // ========================================================
        // Recovery decision
        // ========================================================

        RecoveryDecision recoveryDecision =
                RecoveryDecision.builder()
                        .strategy(
                                RecoveryStrategy.RETRY_PAYMENT
                        )
                        .priority(
                                RecoveryPriority.MEDIUM_HIGH
                        )
                        .recoveryScore(
                                new BigDecimal("0.75")
                        )
                        .reason(
                                "Payment failed with insufficient funds"
                        )
                        .build();


        // ========================================================
        // MOCK: payment idempotency
        // ========================================================

        when(
                paymentAttemptRepository
                        .findByIdempotencyKey(
                                "pay_test_001"
                        )
        ).thenReturn(
                Optional.empty()
        );


        // ========================================================
        // MOCK: subscription lookup
        // ========================================================

        when(
                subscriptionRepository
                        .findByExternalSubscriptionId(
                                "sub_test_001"
                        )
        ).thenReturn(
                Optional.of(subscription)
        );


        // ========================================================
        // MOCK: payment attempt save
        // ========================================================

        when(
                paymentAttemptRepository.save(
                        any(PaymentAttempt.class)
                )
        ).thenReturn(
                savedPaymentAttempt
        );


        // ========================================================
        // MOCK: recovery case save
        //
        // IMPORTANT:
        //
        // The current PaymentRecoveryService saves the
        // RecoveryCase twice:
        //
        // 1. Initial RecoveryCase creation
        // 2. After Tier-2 ML prediction updates score
        //
        // Returning the same object is sufficient for this unit test.
        // ========================================================

        when(
                recoveryCaseRepository.save(
                        any(RecoveryCase.class)
                )
        ).thenReturn(
                savedRecoveryCase
        );


        // ========================================================
        // MOCK: ML feature mapper
        // ========================================================

        when(
                recoveryFeatureMapper.map(
                        any(RecoveryCase.class)
                )
        ).thenReturn(
                predictionRequest
        );


        // ========================================================
        // MOCK: ML prediction service
        // ========================================================

        when(
                recoveryPredictionService.predict(
                        any(RecoveryPredictionRequest.class)
                )
        ).thenReturn(
                predictionResponse
        );


        // ========================================================
        // MOCK: strategy engine
        // ========================================================

        when(
                recoveryStrategyEngine.determineStrategy(
                        any(RecoveryCase.class)
                )
        ).thenReturn(
                recoveryDecision
        );


        // ========================================================
        // MOCK: decision guard
        // ========================================================

        when(
                recoveryDecisionGuard.validate(
                        any(RecoveryCase.class),
                        eq(recoveryDecision)
                )
        ).thenReturn(
                RecoveryDecisionGuard.GuardResult.builder()
                        .allowed(true)
                        .reason(
                                "Recovery decision passed all safety checks"
                        )
                        .build()
        );


        // ========================================================
        // EXECUTE
        // ========================================================

        paymentRecoveryService
                .processPaymentFailure(event);


        // ========================================================
        // VERIFY: payment idempotency
        // ========================================================

        verify(
                paymentAttemptRepository,
                times(1)
        ).findByIdempotencyKey(
                "pay_test_001"
        );


        // ========================================================
        // VERIFY: subscription lookup
        // ========================================================

        verify(
                subscriptionRepository,
                times(1)
        ).findByExternalSubscriptionId(
                "sub_test_001"
        );


        // ========================================================
        // VERIFY: payment attempt
        // ========================================================

        verify(
                paymentAttemptRepository,
                times(1)
        ).save(
                any(PaymentAttempt.class)
        );


        // ========================================================
        // VERIFY: subscription update
        // ========================================================

        verify(
                subscriptionRepository,
                times(1)
        ).save(
                subscription
        );


        // ========================================================
        // VERIFY: recovery case
        //
        // TWO saves are expected.
        // ========================================================

        verify(
                recoveryCaseRepository,
                times(2)
        ).save(
                any(RecoveryCase.class)
        );


        // ========================================================
        // VERIFY: ML feature mapping
        // ========================================================

        verify(
                recoveryFeatureMapper,
                times(1)
        ).map(
                any(RecoveryCase.class)
        );


        // ========================================================
        // VERIFY: ML prediction
        // ========================================================

        verify(
                recoveryPredictionService,
                times(1)
        ).predict(
                any(RecoveryPredictionRequest.class)
        );


        // ========================================================
        // VERIFY: strategy engine
        // ========================================================

        verify(
                recoveryStrategyEngine,
                times(1)
        ).determineStrategy(
                any(RecoveryCase.class)
        );


        // ========================================================
        // VERIFY: decision guard
        // ========================================================

        verify(
                recoveryDecisionGuard,
                times(1)
        ).validate(
                any(RecoveryCase.class),
                eq(recoveryDecision)
        );


        // ========================================================
        // VERIFY: action executor
        // ========================================================

        verify(
                recoveryActionExecutor,
                times(1)
        ).execute(
                any(RecoveryCase.class),
                eq(recoveryDecision)
        );
    }


    // ============================================================
    // 2. DUPLICATE PAYMENT
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
                        .externalPaymentId(
                                "pay_duplicate"
                        )
                        .idempotencyKey(
                                "pay_duplicate"
                        )
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
                times(1)
        ).findByIdempotencyKey(
                "pay_duplicate"
        );


        verify(
                paymentAttemptRepository,
                never()
        ).save(
                any(PaymentAttempt.class)
        );


        verify(
                recoveryCaseRepository,
                never()
        ).save(
                any(RecoveryCase.class)
        );


        verify(
                subscriptionRepository,
                never()
        ).save(
                any(Subscription.class)
        );


        verify(
                recoveryFeatureMapper,
                never()
        ).map(
                any(RecoveryCase.class)
        );


        verify(
                recoveryPredictionService,
                never()
        ).predict(
                any(RecoveryPredictionRequest.class)
        );


        verify(
                recoveryStrategyEngine,
                never()
        ).determineStrategy(
                any(RecoveryCase.class)
        );


        verify(
                recoveryDecisionGuard,
                never()
        ).validate(
                any(RecoveryCase.class),
                any(RecoveryDecision.class)
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
    // 3. NULL EVENT
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
                recoveryDecisionGuard,
                recoveryFeatureMapper,
                recoveryPredictionService,
                recoveryActionExecutor
        );
    }


    // ============================================================
    // 4. MISSING PAYLOAD
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
                recoveryDecisionGuard,
                recoveryFeatureMapper,
                recoveryPredictionService,
                recoveryActionExecutor
        );
    }


    // ============================================================
    // 5. MISSING PAYMENT
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
                recoveryDecisionGuard,
                recoveryFeatureMapper,
                recoveryPredictionService,
                recoveryActionExecutor
        );
    }


    // ============================================================
    // 6. MISSING PAYMENT ID
    // ============================================================

    @Test
    void shouldIgnorePaymentWhenPaymentIdIsMissing() {


        PaymentFailedEvent.Entity entity =
                new PaymentFailedEvent.Entity();

        entity.setSubscriptionId(
                "sub_test_001"
        );


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
                recoveryDecisionGuard,
                recoveryFeatureMapper,
                recoveryPredictionService,
                recoveryActionExecutor
        );
    }


    // ============================================================
    // 7. MISSING SUBSCRIPTION ID
    // ============================================================

    @Test
    void shouldIgnorePaymentWhenSubscriptionIdIsMissing() {


        PaymentFailedEvent.Entity entity =
                new PaymentFailedEvent.Entity();

        entity.setId(
                "pay_test_001"
        );


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
                recoveryDecisionGuard,
                recoveryFeatureMapper,
                recoveryPredictionService,
                recoveryActionExecutor
        );
    }


    // ============================================================
    // 8. SUBSCRIPTION DOES NOT EXIST
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


        // ========================================================
        // Mock idempotency
        // ========================================================

        when(
                paymentAttemptRepository
                        .findByIdempotencyKey(
                                "pay_test_002"
                        )
        ).thenReturn(
                Optional.empty()
        );


        // ========================================================
        // Mock missing subscription
        // ========================================================

        when(
                subscriptionRepository
                        .findByExternalSubscriptionId(
                                "sub_missing"
                        )
        ).thenReturn(
                Optional.empty()
        );


        // ========================================================
        // Execute
        // ========================================================

        paymentRecoveryService
                .processPaymentFailure(event);


        // ========================================================
        // Verify subscription lookup
        // ========================================================

        verify(
                subscriptionRepository,
                times(1)
        ).findByExternalSubscriptionId(
                "sub_missing"
        );


        // ========================================================
        // Nothing after subscription lookup should execute
        // ========================================================

        verify(
                paymentAttemptRepository,
                never()
        ).save(
                any(PaymentAttempt.class)
        );


        verify(
                recoveryCaseRepository,
                never()
        ).save(
                any(RecoveryCase.class)
        );


        verify(
                subscriptionRepository,
                never()
        ).save(
                any(Subscription.class)
        );


        verify(
                recoveryFeatureMapper,
                never()
        ).map(
                any(RecoveryCase.class)
        );


        verify(
                recoveryPredictionService,
                never()
        ).predict(
                any(RecoveryPredictionRequest.class)
        );


        verify(
                recoveryStrategyEngine,
                never()
        ).determineStrategy(
                any(RecoveryCase.class)
        );


        verify(
                recoveryDecisionGuard,
                never()
        ).validate(
                any(RecoveryCase.class),
                any(RecoveryDecision.class)
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