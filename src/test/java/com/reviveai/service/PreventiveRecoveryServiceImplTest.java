package com.reviveai.service;

import com.reviveai.entity.RecoveryAction;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.entity.Subscription;
import com.reviveai.entity.SubscriptionHealth;
import com.reviveai.recovery.RecoveryPriority;
import com.reviveai.recovery.RecoveryStrategy;
import com.reviveai.repository.RecoveryActionRepository;
import com.reviveai.repository.RecoveryCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PreventiveRecoveryServiceImplTest {

    @Mock
    private RecoveryCaseRepository recoveryCaseRepository;

    @Mock
    private RecoveryActionRepository recoveryActionRepository;

    @InjectMocks
    private PreventiveRecoveryServiceImpl preventiveRecoveryService;

    private Subscription subscription;

    private SubscriptionHealth health;

    private RecoveryCase recoveryCase;

    @BeforeEach
    void setUp() {

        subscription =
                Subscription.builder()
                        .id(UUID.randomUUID())
                        .build();

        health =
                SubscriptionHealth.builder()
                        .id(UUID.randomUUID())
                        .subscription(subscription)
                        .healthScore(new BigDecimal("0.90"))
                        .riskLevel(
                                SubscriptionHealth.RiskLevel.HEALTHY
                        )
                        .successfulPaymentCount(5)
                        .failedPaymentCount(0)
                        .paymentFailureRate(BigDecimal.ZERO)
                        .consecutiveFailures(0)
                        .recentFailureCount(0)
                        .paymentBehaviorDeclining(false)
                        .preventiveActionTriggered(false)
                        .build();

        recoveryCase =
                RecoveryCase.builder()
                        .id(UUID.randomUUID())
                        .subscription(subscription)
                        .status(
                                RecoveryCase.RecoveryStatus.OPEN
                        )
                        .recoveryPotential(
                                RecoveryCase.RecoveryPotential.HIGH
                        )
                        .recoveryScore(
                                new BigDecimal("0.80")
                        )
                        .amountAtRisk(
                                new BigDecimal("70.00")
                        )
                        .amountRecovered(
                                BigDecimal.ZERO
                        )
                        .build();
    }

    // ============================================================
    // 1. HEALTHY SUBSCRIPTION
    // ============================================================

    @Test
    void shouldNotTriggerPreventiveRecoveryForHealthySubscription() {

        boolean result =
                preventiveRecoveryService
                        .shouldTriggerPreventiveRecovery(
                                health
                        );

        assertFalse(result);
    }

    // ============================================================
    // 2. LOW HEALTH SCORE
    // ============================================================

    @Test
    void shouldTriggerWhenHealthScoreIsBelowThreshold() {

        health.setHealthScore(
                new BigDecimal("0.50")
        );

        boolean result =
                preventiveRecoveryService
                        .shouldTriggerPreventiveRecovery(
                                health
                        );

        assertTrue(result);
    }

    // ============================================================
    // 3. CONSECUTIVE FAILURES
    // ============================================================

    @Test
    void shouldTriggerWhenConsecutiveFailuresReachThreshold() {

        health.setConsecutiveFailures(2);

        boolean result =
                preventiveRecoveryService
                        .shouldTriggerPreventiveRecovery(
                                health
                        );

        assertTrue(result);
    }

    // ============================================================
    // 4. RECENT FAILURES
    // ============================================================

    @Test
    void shouldTriggerWhenRecentFailuresReachThreshold() {

        health.setRecentFailureCount(3);

        boolean result =
                preventiveRecoveryService
                        .shouldTriggerPreventiveRecovery(
                                health
                        );

        assertTrue(result);
    }

    // ============================================================
    // 5. PAYMENT BEHAVIOR DECLINING
    // ============================================================

    @Test
    void shouldTriggerWhenPaymentBehaviorIsDeclining() {

        health.setPaymentBehaviorDeclining(true);

        boolean result =
                preventiveRecoveryService
                        .shouldTriggerPreventiveRecovery(
                                health
                        );

        assertTrue(result);
    }

    // ============================================================
    // 6. EXISTING PREVENTIVE ACTION
    // ============================================================

    @Test
    void shouldNotTriggerWhenPreventiveActionAlreadyTriggered() {

        health.setPreventiveActionTriggered(true);

        boolean result =
                preventiveRecoveryService
                        .shouldTriggerPreventiveRecovery(
                                health
                        );

        assertFalse(result);
    }

    // ============================================================
    // 7. CRITICAL RISK
    // ============================================================

    @Test
    void shouldTriggerForCriticalRisk() {

        health.setRiskLevel(
                SubscriptionHealth.RiskLevel.CRITICAL
        );

        boolean result =
                preventiveRecoveryService
                        .shouldTriggerPreventiveRecovery(
                                health
                        );

        assertTrue(result);
    }

    // ============================================================
    // 8. CREATE RETRY PAYMENT ACTION
    // ============================================================

    @Test
    void shouldCreateRetryPaymentAction() {

        health.setHealthScore(
                new BigDecimal("0.50")
        );

        health.setRiskLevel(
                SubscriptionHealth.RiskLevel.MEDIUM
        );

        when(
                recoveryCaseRepository
                        .findBySubscriptionId(
                                subscription.getId()
                        )
        ).thenReturn(
                List.of(recoveryCase)
        );

        when(
                recoveryActionRepository
                        .findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                                recoveryCase.getId(),
                                RecoveryStrategy.RETRY_PAYMENT
                        )
        ).thenReturn(Optional.empty());

        when(
                recoveryActionRepository.save(any(RecoveryAction.class))
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        RecoveryAction result =
                preventiveRecoveryService
                        .createPreventiveRecoveryAction(
                                health
                        );

        assertNotNull(result);

        assertEquals(
                recoveryCase,
                result.getRecoveryCase()
        );

        assertEquals(
                RecoveryStrategy.RETRY_PAYMENT,
                result.getStrategy()
        );

        assertEquals(
                RecoveryPriority.MEDIUM,
                result.getPriority()
        );

        assertEquals(
                RecoveryAction.ActionStatus.PENDING,
                result.getStatus()
        );

        assertTrue(
                health.getPreventiveActionTriggered()
        );

        verify(
                recoveryActionRepository,
                times(1)
        ).save(any(RecoveryAction.class));
    }

    // ============================================================
    // 9. UPDATE PAYMENT METHOD
    // ============================================================

    @Test
    void shouldCreateUpdatePaymentMethodActionAfterRepeatedFailures() {

        health.setConsecutiveFailures(3);

        health.setRiskLevel(
                SubscriptionHealth.RiskLevel.MEDIUM
        );

        when(
                recoveryCaseRepository
                        .findBySubscriptionId(
                                subscription.getId()
                        )
        ).thenReturn(
                List.of(recoveryCase)
        );

        when(
                recoveryActionRepository
                        .findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                                recoveryCase.getId(),
                                RecoveryStrategy.UPDATE_PAYMENT_METHOD
                        )
        ).thenReturn(Optional.empty());

        when(
                recoveryActionRepository.save(any(RecoveryAction.class))
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        RecoveryAction result =
                preventiveRecoveryService
                        .createPreventiveRecoveryAction(
                                health
                        );

        assertNotNull(result);

        assertEquals(
                RecoveryStrategy.UPDATE_PAYMENT_METHOD,
                result.getStrategy()
        );

        assertEquals(
                RecoveryPriority.MEDIUM,
                result.getPriority()
        );

        assertEquals(
                RecoveryAction.ActionStatus.PENDING,
                result.getStatus()
        );

        assertTrue(
                health.getPreventiveActionTriggered()
        );
    }

    // ============================================================
    // 10. NO ACTIVE RECOVERY CASE
    // ============================================================

    @Test
    void shouldThrowWhenNoActiveRecoveryCaseExists() {

        health.setHealthScore(
                new BigDecimal("0.50")
        );

        when(
                recoveryCaseRepository
                        .findBySubscriptionId(
                                subscription.getId()
                        )
        ).thenReturn(List.of());

        assertThrows(
                IllegalStateException.class,
                () ->
                        preventiveRecoveryService
                                .createPreventiveRecoveryAction(
                                        health
                                )
        );

        verify(
                recoveryActionRepository,
                never()
        ).save(any(RecoveryAction.class));
    }

    // ============================================================
    // 11. DUPLICATE STRATEGY
    // ============================================================

    @Test
    void shouldReturnExistingActionInsteadOfCreatingDuplicate() {

        health.setHealthScore(
                new BigDecimal("0.50")
        );

        RecoveryAction existingAction =
                RecoveryAction.builder()
                        .id(100L)
                        .recoveryCase(recoveryCase)
                        .strategy(
                                RecoveryStrategy.RETRY_PAYMENT
                        )
                        .priority(
                                RecoveryPriority.MEDIUM
                        )
                        .recoveryScore(
                                new BigDecimal("0.50")
                        )
                        .status(
                                RecoveryAction.ActionStatus.PENDING
                        )
                        .reason(
                                "Existing preventive action"
                        )
                        .build();

        when(
                recoveryCaseRepository
                        .findBySubscriptionId(
                                subscription.getId()
                        )
        ).thenReturn(
                List.of(recoveryCase)
        );

        when(
                recoveryActionRepository
                        .findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                                recoveryCase.getId(),
                                RecoveryStrategy.RETRY_PAYMENT
                        )
        ).thenReturn(
                Optional.of(existingAction)
        );

        RecoveryAction result =
                preventiveRecoveryService
                        .createPreventiveRecoveryAction(
                                health
                        );

        assertNotNull(result);

        assertEquals(
                existingAction,
                result
        );

        assertTrue(
                health.getPreventiveActionTriggered()
        );

        verify(
                recoveryActionRepository,
                never()
        ).save(any(RecoveryAction.class));
    }

    // ============================================================
    // 12. NULL HEALTH
    // ============================================================

    @Test
    void shouldNotTriggerWhenHealthIsNull() {

        boolean result =
                preventiveRecoveryService
                        .shouldTriggerPreventiveRecovery(
                                null
                        );

        assertFalse(result);
    }
}

