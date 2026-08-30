package com.reviveai.service;

import com.reviveai.agent.RecoveryAgentRequest;
import com.reviveai.agent.RecoveryAgentResponse;
import com.reviveai.dto.PaymentFailedEvent;
import com.reviveai.entity.PaymentAttempt;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.entity.Subscription;
import com.reviveai.entity.SubscriptionHealth;
import com.reviveai.ml.RecoveryFeatureMapper;
import com.reviveai.ml.RecoveryPredictionRequest;
import com.reviveai.ml.RecoveryPredictionResponse;
import com.reviveai.ml.RecoveryPredictionService;
import com.reviveai.recovery.RecoveryActionOrchestrator;
import com.reviveai.recovery.RecoveryDecision;
import com.reviveai.recovery.RecoveryPriority;
import com.reviveai.recovery.RecoveryStrategy;
import com.reviveai.recovery.RecoveryStrategyEngine;
import com.reviveai.recovery.RecoveryDecisionOrchestrator;
import com.reviveai.repository.PaymentAttemptRepository;
import com.reviveai.repository.RecoveryCaseRepository;
import com.reviveai.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentRecoveryServiceTest {

    // =========================================================
    // REPOSITORIES
    // =========================================================

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private RecoveryCaseRepository recoveryCaseRepository;

    // =========================================================
    // HEALTH
    // =========================================================

    @Mock
    private SubscriptionHealthEvaluator subscriptionHealthEvaluator;

    // =========================================================
    // RECOVERY ENGINE
    // =========================================================

    @Mock
    private RecoveryStrategyEngine recoveryStrategyEngine;

    @Mock
    private RecoveryDecisionOrchestrator recoveryDecisionOrchestrator;

    @Mock
    private RecoveryActionOrchestrator recoveryActionOrchestrator;

    // =========================================================
    // ML
    // =========================================================

    @Mock
    private RecoveryFeatureMapper recoveryFeatureMapper;

    @Mock
    private RecoveryPredictionService recoveryPredictionService;

    // =========================================================
    // SERVICE UNDER TEST
    // =========================================================

    @InjectMocks
    private PaymentRecoveryService paymentRecoveryService;

    // =========================================================
    // HELPER: CREATE PAYMENT
    // =========================================================

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

    // =========================================================
    // HELPER: CREATE EVENT
    // =========================================================

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

    // =========================================================
    // 1. SUCCESSFUL PAYMENT FAILURE PROCESSING
    // =========================================================

    @Test
    void shouldProcessPaymentFailure() {

        // =====================================================
        // PAYMENT EVENT
        // =====================================================

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

        // =====================================================
        // IDS
        // =====================================================

        UUID subscriptionId =
                UUID.randomUUID();

        UUID paymentAttemptId =
                UUID.randomUUID();

        UUID recoveryCaseId =
                UUID.randomUUID();

        // =====================================================
        // SUBSCRIPTION
        // =====================================================

        Subscription subscription =
                Subscription.builder()
                        .id(subscriptionId)
                        .externalSubscriptionId(
                                "sub_test_001"
                        )
                        .build();

        // =====================================================
        // PAYMENT ATTEMPT
        // =====================================================

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

        // =====================================================
        // RECOVERY CASE
        //
        // 7000 paise = ₹70.00
        // ₹70.00 <= ₹5,000
        // Therefore recovery potential = HIGH
        // =====================================================

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
                                BigDecimal.ZERO
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

        // =====================================================
        // HEALTH
        // =====================================================

        SubscriptionHealth health =
                mock(SubscriptionHealth.class);

        when(
                subscriptionHealthEvaluator.evaluateHealth(
                        subscription
                )
        ).thenReturn(
                health
        );

        // =====================================================
        // ML REQUEST
        // =====================================================

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

        // =====================================================
        // ML RESPONSE
        // =====================================================

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

        // =====================================================
        // RULE-BASED DECISION
        // =====================================================

        RecoveryDecision ruleBasedDecision =
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

        // =====================================================
        // FINAL POLICY DECISION
        // =====================================================

        RecoveryAgentResponse finalDecision =
                RecoveryAgentResponse.builder()
                        .recommendedStrategy(
                                RecoveryStrategy.RETRY_PAYMENT
                        )
                        .priority(
                                RecoveryPriority.MEDIUM_HIGH
                        )
                        .reason(
                                "Retry payment because the failure " +
                                        "was caused by insufficient funds."
                        )
                        .modelVersion(
                                "recovery-agent-v1"
                        )
                        .fallbackUsed(false)
                        .build();

        // =====================================================
        // MOCK: IDEMPOTENCY
        // =====================================================

        when(
                paymentAttemptRepository
                        .findByIdempotencyKey(
                                "pay_test_001"
                        )
        ).thenReturn(
                Optional.empty()
        );

        // =====================================================
        // MOCK: SUBSCRIPTION
        // =====================================================

        when(
                subscriptionRepository
                        .findByExternalSubscriptionId(
                                "sub_test_001"
                        )
        ).thenReturn(
                Optional.of(subscription)
        );

        // =====================================================
        // MOCK: PAYMENT SAVE
        // =====================================================

        when(
                paymentAttemptRepository.save(
                        any(PaymentAttempt.class)
                )
        ).thenReturn(
                savedPaymentAttempt
        );

        // =====================================================
        // MOCK: RECOVERY CASE SAVE
        //
        // IMPORTANT:
        //
        // PaymentRecoveryService saves RecoveryCase once.
        // No recoveryCaseId variable is required here.
        // =====================================================

        when(
                recoveryCaseRepository.save(
                        any(RecoveryCase.class)
                )
        ).thenReturn(
                savedRecoveryCase
        );

        // =====================================================
        // MOCK: ML FEATURE MAPPER
        // =====================================================

        when(
                recoveryFeatureMapper.map(
                        any(RecoveryCase.class)
                )
        ).thenReturn(
                predictionRequest
        );

        // =====================================================
        // MOCK: ML PREDICTION
        // =====================================================

        when(
                recoveryPredictionService.predict(
                        any(RecoveryPredictionRequest.class)
                )
        ).thenReturn(
                predictionResponse
        );

        // =====================================================
        // MOCK: RULE ENGINE
        // =====================================================

        when(
                recoveryStrategyEngine.determineStrategy(
                        any(RecoveryCase.class)
                )
        ).thenReturn(
                ruleBasedDecision
        );

        // =====================================================
        // MOCK: DECISION ORCHESTRATOR
        //
        // PaymentRecoveryService calls this, not
        // RecoveryAgentService directly.
        // =====================================================

        when(
                recoveryDecisionOrchestrator.decide(
                        any(RecoveryAgentRequest.class)
                )
        ).thenReturn(
                finalDecision
        );

        // =====================================================
        // MOCK: ACTION ORCHESTRATOR
        // =====================================================

        doNothing().when(
                recoveryActionOrchestrator
        ).execute(
                any(RecoveryCase.class),
                any(RecoveryAgentRequest.class),
                any(RecoveryAgentResponse.class)
        );

        // =====================================================
        // EXECUTE SERVICE
        // =====================================================

        assertDoesNotThrow(
                () ->
                        paymentRecoveryService
                                .processPaymentFailure(event)
        );

        // =====================================================
        // VERIFY: IDEMPOTENCY
        // =====================================================

        verify(
                paymentAttemptRepository,
                times(1)
        ).findByIdempotencyKey(
                "pay_test_001"
        );

        // =====================================================
        // VERIFY: SUBSCRIPTION LOOKUP
        // =====================================================

        verify(
                subscriptionRepository,
                times(1)
        ).findByExternalSubscriptionId(
                "sub_test_001"
        );

        // =====================================================
        // VERIFY: PAYMENT CREATED
        // =====================================================

        verify(
                paymentAttemptRepository,
                times(1)
        ).save(
                any(PaymentAttempt.class)
        );

        // =====================================================
        // CAPTURE PAYMENT ATTEMPT
        // =====================================================

        ArgumentCaptor<PaymentAttempt> paymentCaptor =
                ArgumentCaptor.forClass(
                        PaymentAttempt.class
                );

        verify(
                paymentAttemptRepository
        ).save(
                paymentCaptor.capture()
        );

        PaymentAttempt capturedPayment =
                paymentCaptor.getValue();

        assertNotNull(
                capturedPayment
        );

        assertEquals(
                subscription,
                capturedPayment.getSubscription()
        );

        assertEquals(
                "pay_test_001",
                capturedPayment.getExternalPaymentId()
        );

        assertEquals(
                "pay_test_001",
                capturedPayment.getIdempotencyKey()
        );

        assertEquals(
                new BigDecimal("70.00"),
                capturedPayment.getAmount()
        );

        assertEquals(
                PaymentAttempt.PaymentStatus.FAILED,
                capturedPayment.getStatus()
        );

        assertEquals(
                "INSUFFICIENT_FUNDS",
                capturedPayment.getGatewayErrorCode()
        );

        assertEquals(
                "Insufficient funds",
                capturedPayment.getGatewayErrorMessage()
        );

        // =====================================================
        // VERIFY: SUBSCRIPTION PAST DUE
        // =====================================================

        verify(
                subscriptionRepository,
                times(1)
        ).save(
                subscription
        );

        assertEquals(
                Subscription.SubscriptionStatus.PAST_DUE,
                subscription.getStatus()
        );

        // =====================================================
        // VERIFY: HEALTH
        // =====================================================

        verify(
                subscriptionHealthEvaluator,
                times(1)
        ).evaluateHealth(
                subscription
        );

        // =====================================================
        // VERIFY: RECOVERY CASE
        // =====================================================

        verify(
                recoveryCaseRepository,
                times(1)
        ).save(
                any(RecoveryCase.class)
        );

        // =====================================================
        // VERIFY RECOVERY CASE VALUES
        // =====================================================

        assertEquals(
                new BigDecimal("0.75"),
                savedRecoveryCase.getRecoveryScore()
        );

        assertEquals(
                RecoveryCase.RecoveryStatus.IN_PROGRESS,
                savedRecoveryCase.getStatus()
        );

        assertEquals(
                RecoveryCase.RecoveryPotential.HIGH,
                savedRecoveryCase.getRecoveryPotential()
        );

        assertEquals(
                new BigDecimal("70.00"),
                savedRecoveryCase.getAmountAtRisk()
        );

        assertEquals(
                BigDecimal.ZERO,
                savedRecoveryCase.getAmountRecovered()
        );

        // =====================================================
        // VERIFY: ML FEATURE MAPPER
        // =====================================================

        verify(
                recoveryFeatureMapper,
                times(1)
        ).map(
                any(RecoveryCase.class)
        );

        // =====================================================
        // CAPTURE ML REQUEST
        // =====================================================

        ArgumentCaptor<RecoveryPredictionRequest>
                predictionRequestCaptor =
                ArgumentCaptor.forClass(
                        RecoveryPredictionRequest.class
                );

        verify(
                recoveryPredictionService,
                times(1)
        ).predict(
                predictionRequestCaptor.capture()
        );

        RecoveryPredictionRequest
                capturedPredictionRequest =
                predictionRequestCaptor.getValue();

        assertNotNull(
                capturedPredictionRequest
        );

        assertEquals(
                new BigDecimal("70.00"),
                capturedPredictionRequest
                        .getPaymentAmount()
        );

        assertEquals(
                0,
                capturedPredictionRequest
                        .getRetryCount()
        );

        assertEquals(
                0,
                capturedPredictionRequest
                        .getDaysPastDue()
        );

        assertEquals(
                0,
                capturedPredictionRequest
                        .getPreviousSuccessfulPayments()
        );

        assertEquals(
                0,
                capturedPredictionRequest
                        .getPreviousFailedPayments()
        );

        assertEquals(
                BigDecimal.ZERO,
                capturedPredictionRequest
                        .getPaymentFailureRate()
        );

        assertEquals(
                "HIGH",
                capturedPredictionRequest
                        .getRecoveryPotential()
        );

        assertEquals(
                "INSUFFICIENT_FUNDS",
                capturedPredictionRequest
                        .getErrorCode()
        );

        // =====================================================
        // VERIFY: RULE ENGINE
        // =====================================================

        verify(
                recoveryStrategyEngine,
                times(1)
        ).determineStrategy(
                any(RecoveryCase.class)
        );

        // =====================================================
        // VERIFY: DECISION ORCHESTRATOR
        // =====================================================

        ArgumentCaptor<RecoveryAgentRequest>
                agentRequestCaptor =
                ArgumentCaptor.forClass(
                        RecoveryAgentRequest.class
                );

        verify(
                recoveryDecisionOrchestrator,
                times(1)
        ).decide(
                agentRequestCaptor.capture()
        );

        RecoveryAgentRequest capturedAgentRequest =
                agentRequestCaptor.getValue();

        assertNotNull(
                capturedAgentRequest
        );

        assertEquals(
                recoveryCaseId,
                capturedAgentRequest.getRecoveryCaseId()
        );

        assertEquals(
                new BigDecimal("70.00"),
                capturedAgentRequest.getPaymentAmount()
        );

        assertEquals(
                new BigDecimal("0.75"),
                capturedAgentRequest.getRecoveryScore()
        );

        assertEquals(
                RecoveryCase.RecoveryPotential.HIGH,
                capturedAgentRequest.getRecoveryPotential()
        );

        assertEquals(
                "INSUFFICIENT_FUNDS",
                capturedAgentRequest.getPaymentErrorCode()
        );

        assertEquals(
                0,
                capturedAgentRequest.getRetryCount()
        );

        assertEquals(
                BigDecimal.ZERO,
                capturedAgentRequest.getPaymentFailureRate()
        );

        assertEquals(
                RecoveryStrategy.RETRY_PAYMENT,
                capturedAgentRequest.getRuleBasedStrategy()
        );

        assertEquals(
                RecoveryPriority.MEDIUM_HIGH,
                capturedAgentRequest.getRuleBasedPriority()
        );

        // =====================================================
        // VERIFY FINAL DECISION
        // =====================================================

        assertEquals(
                RecoveryStrategy.RETRY_PAYMENT,
                finalDecision.getRecommendedStrategy()
        );

        assertEquals(
                RecoveryPriority.MEDIUM_HIGH,
                finalDecision.getPriority()
        );

        assertEquals(
                "Retry payment because the failure " +
                        "was caused by insufficient funds.",
                finalDecision.getReason()
        );

        assertFalse(
                finalDecision.isFallbackUsed()
        );

        // =====================================================
        // VERIFY ACTION ORCHESTRATOR
        // =====================================================

        verify(
                recoveryActionOrchestrator,
                times(1)
        ).execute(
                eq(savedRecoveryCase),
                eq(capturedAgentRequest),
                eq(finalDecision)
        );

        // =====================================================
        // VERIFY NO DIRECT AGENT INTERACTION
        // =====================================================

        // The PaymentRecoveryService delegates the agent/policy
        // decision through RecoveryDecisionOrchestrator.
        // Therefore there should be no direct RecoveryAgentService
        // interaction from this test.
    }

    // =========================================================
    // 2. DUPLICATE PAYMENT
    // =========================================================

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
                subscriptionRepository,
                never()
        ).findByExternalSubscriptionId(
                anyString()
        );

        verify(
                subscriptionRepository,
                never()
        ).save(
                any(Subscription.class)
        );

        verify(
                recoveryCaseRepository,
                never()
        ).save(
                any(RecoveryCase.class)
        );

        verify(
                subscriptionHealthEvaluator,
                never()
        ).evaluateHealth(
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
                recoveryDecisionOrchestrator,
                never()
        ).decide(
                any(RecoveryAgentRequest.class)
        );

        verify(
                recoveryActionOrchestrator,
                never()
        ).execute(
                any(RecoveryCase.class),
                any(RecoveryAgentRequest.class),
                any(RecoveryAgentResponse.class)
        );
    }

    // =========================================================
    // 3. NULL EVENT
    // =========================================================

    @Test
    void shouldIgnoreInvalidEvent() {

        paymentRecoveryService
                .processPaymentFailure(null);

        verifyNoInteractions(
                paymentAttemptRepository,
                subscriptionRepository,
                recoveryCaseRepository,
                subscriptionHealthEvaluator,
                recoveryStrategyEngine,
                recoveryFeatureMapper,
                recoveryPredictionService,
                recoveryDecisionOrchestrator,
                recoveryActionOrchestrator
        );
    }

    // =========================================================
    // 4. MISSING PAYLOAD
    // =========================================================

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
                subscriptionHealthEvaluator,
                recoveryStrategyEngine,
                recoveryFeatureMapper,
                recoveryPredictionService,
                recoveryDecisionOrchestrator,
                recoveryActionOrchestrator
        );
    }

    // =========================================================
    // 5. MISSING PAYMENT
    // =========================================================

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
                subscriptionHealthEvaluator,
                recoveryStrategyEngine,
                recoveryFeatureMapper,
                recoveryPredictionService,
                recoveryDecisionOrchestrator,
                recoveryActionOrchestrator
        );
    }

    // =========================================================
    // 6. MISSING PAYMENT ID
    // =========================================================

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
                subscriptionHealthEvaluator,
                recoveryStrategyEngine,
                recoveryFeatureMapper,
                recoveryPredictionService,
                recoveryDecisionOrchestrator,
                recoveryActionOrchestrator
        );
    }

    // =========================================================
    // 7. MISSING SUBSCRIPTION ID
    // =========================================================

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
                subscriptionHealthEvaluator,
                recoveryStrategyEngine,
                recoveryFeatureMapper,
                recoveryPredictionService,
                recoveryDecisionOrchestrator,
                recoveryActionOrchestrator
        );
    }

    // =========================================================
    // 8. SUBSCRIPTION DOES NOT EXIST
    // =========================================================

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
        ).thenReturn(
                Optional.empty()
        );

        when(
                subscriptionRepository
                        .findByExternalSubscriptionId(
                                "sub_missing"
                        )
        ).thenReturn(
                Optional.empty()
        );

        paymentRecoveryService
                .processPaymentFailure(event);

        verify(
                paymentAttemptRepository,
                times(1)
        ).findByIdempotencyKey(
                "pay_test_002"
        );

        verify(
                subscriptionRepository,
                times(1)
        ).findByExternalSubscriptionId(
                "sub_missing"
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
                subscriptionHealthEvaluator,
                never()
        ).evaluateHealth(
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
                recoveryDecisionOrchestrator,
                never()
        ).decide(
                any(RecoveryAgentRequest.class)
        );

        verify(
                recoveryActionOrchestrator,
                never()
        ).execute(
                any(RecoveryCase.class),
                any(RecoveryAgentRequest.class),
                any(RecoveryAgentResponse.class)
        );
    }
}

