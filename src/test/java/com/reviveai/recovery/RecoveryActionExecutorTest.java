package com.reviveai.recovery;

import com.reviveai.entity.RecoveryAction;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.repository.RecoveryActionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class RecoveryActionExecutorTest {

    @Mock
    private RecoveryActionRepository recoveryActionRepository;

    @Mock
    private RecoveryDecisionGuard recoveryDecisionGuard;

    @Mock
    private RetryPaymentHandler retryPaymentHandler;

    @Mock
    private UpdatePaymentMethodHandler updatePaymentMethodHandler;

    @Mock
    private RecoveryActionHandler customerActionHandler;

    @Mock
    private RecoveryActionHandler manualReviewHandler;

    private RecoveryActionExecutor recoveryActionExecutor;

    // ============================================================
    // SETUP
    // ============================================================

    @BeforeEach
    void setUp() {

        /*
         * RecoveryActionExecutor builds its handler map inside
         * the constructor.
         *
         * Therefore all handlers must expose their strategy
         * before the executor is constructed.
         *
         * lenient() is intentional because individual tests
         * execute only one strategy, while all handlers are still
         * required to construct the executor's handler registry.
         */

        lenient().when(retryPaymentHandler.getStrategy())
                .thenReturn(RecoveryStrategy.RETRY_PAYMENT);

        lenient().when(updatePaymentMethodHandler.getStrategy())
                .thenReturn(
                        RecoveryStrategy.UPDATE_PAYMENT_METHOD
                );

        lenient().when(customerActionHandler.getStrategy())
                .thenReturn(
                        RecoveryStrategy.CUSTOMER_ACTION_REQUIRED
                );

        lenient().when(manualReviewHandler.getStrategy())
                .thenReturn(
                        RecoveryStrategy.MANUAL_REVIEW
                );

        recoveryActionExecutor =
                new RecoveryActionExecutor(
                        recoveryActionRepository,
                        List.of(
                                retryPaymentHandler,
                                updatePaymentMethodHandler,
                                customerActionHandler,
                                manualReviewHandler
                        ),
                        recoveryDecisionGuard
                );
    }

    // ============================================================
    // RETRY PAYMENT
    // ============================================================

    @Test
    void shouldCreateAndExecuteRecoveryAction() {

        RecoveryCase recoveryCase =
                RecoveryCase.builder()
                        .id(UUID.randomUUID())
                        .build();

        RecoveryDecision decision =
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

        approveDecision(
                recoveryCase,
                decision
        );

        when(
                recoveryActionRepository
                        .findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                                recoveryCase.getId(),
                                RecoveryStrategy.RETRY_PAYMENT
                        )
        ).thenReturn(Optional.empty());

        RecoveryAction pendingAction =
                RecoveryAction.builder()
                        .id(1L)
                        .recoveryCase(recoveryCase)
                        .strategy(
                                RecoveryStrategy.RETRY_PAYMENT
                        )
                        .priority(
                                RecoveryPriority.MEDIUM_HIGH
                        )
                        .recoveryScore(
                                new BigDecimal("0.70")
                        )
                        .status(
                                RecoveryAction.ActionStatus.PENDING
                        )
                        .reason(
                                "Payment failed with insufficient funds"
                        )
                        .build();

        when(
                recoveryActionRepository.save(
                        any(RecoveryAction.class)
                )
        ).thenReturn(pendingAction);

        RecoveryOutcome outcome =
                new RecoveryOutcome(
                        RecoveryOutcome.OutcomeStatus.FAILED,
                        BigDecimal.ZERO,
                        "Payment retry request has not yet been integrated with Razorpay."
                );

        when(
                retryPaymentHandler.handle(
                        recoveryCase,
                        decision
                )
        ).thenReturn(outcome);

        recoveryActionExecutor.execute(
                recoveryCase,
                decision
        );

        ArgumentCaptor<RecoveryAction> captor =
                ArgumentCaptor.forClass(
                        RecoveryAction.class
                );

        verify(
                recoveryActionRepository,
                times(2)
        ).save(captor.capture());

        List<RecoveryAction> savedActions =
                captor.getAllValues();

        assertEquals(
                2,
                savedActions.size()
        );

        RecoveryAction firstSavedAction =
                savedActions.get(0);

        RecoveryAction secondSavedAction =
                savedActions.get(1);

        assertEquals(
                RecoveryAction.ActionStatus.PENDING,
                firstSavedAction.getStatus()
        );

        assertEquals(
                RecoveryStrategy.RETRY_PAYMENT,
                firstSavedAction.getStrategy()
        );

        assertEquals(
                RecoveryPriority.MEDIUM_HIGH,
                firstSavedAction.getPriority()
        );

        assertEquals(
                new BigDecimal("0.70"),
                firstSavedAction.getRecoveryScore()
        );

        assertEquals(
                "Payment failed with insufficient funds",
                firstSavedAction.getReason()
        );

        assertEquals(
                RecoveryAction.ActionStatus.EXECUTED,
                secondSavedAction.getStatus()
        );

        assertNotNull(
                secondSavedAction.getExecutedAt()
        );

        verify(
                recoveryDecisionGuard,
                times(1)
        ).validate(
                recoveryCase,
                decision
        );

        verify(
                retryPaymentHandler,
                times(1)
        ).handle(
                recoveryCase,
                decision
        );

        verify(
                updatePaymentMethodHandler,
                never()
        ).handle(any(), any());

        verify(
                customerActionHandler,
                never()
        ).handle(any(), any());

        verify(
                manualReviewHandler,
                never()
        ).handle(any(), any());
    }

    // ============================================================
    // UPDATE PAYMENT METHOD
    // ============================================================

    @Test
    void shouldExecuteUpdatePaymentMethodStrategy() {

        RecoveryCase recoveryCase =
                RecoveryCase.builder()
                        .id(UUID.randomUUID())
                        .build();

        RecoveryDecision decision =
                RecoveryDecision.builder()
                        .strategy(
                                RecoveryStrategy.UPDATE_PAYMENT_METHOD
                        )
                        .priority(
                                RecoveryPriority.HIGH
                        )
                        .recoveryScore(
                                new BigDecimal("0.80")
                        )
                        .reason(
                                "Card expired"
                        )
                        .build();

        approveDecision(
                recoveryCase,
                decision
        );

        when(
                recoveryActionRepository
                        .findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                                recoveryCase.getId(),
                                RecoveryStrategy.UPDATE_PAYMENT_METHOD
                        )
        ).thenReturn(Optional.empty());

        RecoveryAction pendingAction =
                RecoveryAction.builder()
                        .id(2L)
                        .recoveryCase(recoveryCase)
                        .strategy(
                                RecoveryStrategy.UPDATE_PAYMENT_METHOD
                        )
                        .priority(
                                RecoveryPriority.HIGH
                        )
                        .recoveryScore(
                                new BigDecimal("0.80")
                        )
                        .status(
                                RecoveryAction.ActionStatus.PENDING
                        )
                        .reason(
                                "Card expired"
                        )
                        .build();

        when(
                recoveryActionRepository.save(
                        any(RecoveryAction.class)
                )
        ).thenReturn(pendingAction);

        RecoveryOutcome outcome =
                new RecoveryOutcome(
                        RecoveryOutcome.OutcomeStatus.FAILED,
                        BigDecimal.ZERO,
                        "Customer payment method update is required."
                );

        when(
                updatePaymentMethodHandler.handle(
                        recoveryCase,
                        decision
                )
        ).thenReturn(outcome);

        recoveryActionExecutor.execute(
                recoveryCase,
                decision
        );

        verify(
                recoveryActionRepository,
                times(2)
        ).save(any(RecoveryAction.class));

        verify(
                updatePaymentMethodHandler,
                times(1)
        ).handle(
                recoveryCase,
                decision
        );

        verify(
                retryPaymentHandler,
                never()
        ).handle(any(), any());

        verify(
                customerActionHandler,
                never()
        ).handle(any(), any());

        verify(
                manualReviewHandler,
                never()
        ).handle(any(), any());

        assertEquals(
                RecoveryAction.ActionStatus.EXECUTED,
                pendingAction.getStatus()
        );

        assertNotNull(
                pendingAction.getExecutedAt()
        );
    }

    // ============================================================
    // CUSTOMER ACTION REQUIRED
    // ============================================================

    @Test
    void shouldExecuteCustomerActionRequiredStrategy() {

        RecoveryCase recoveryCase =
                RecoveryCase.builder()
                        .id(UUID.randomUUID())
                        .build();

        RecoveryDecision decision =
                RecoveryDecision.builder()
                        .strategy(
                                RecoveryStrategy.CUSTOMER_ACTION_REQUIRED
                        )
                        .priority(
                                RecoveryPriority.MEDIUM
                        )
                        .recoveryScore(
                                new BigDecimal("0.60")
                        )
                        .reason(
                                "Customer authentication required"
                        )
                        .build();

        approveDecision(
                recoveryCase,
                decision
        );

        when(
                recoveryActionRepository
                        .findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                                recoveryCase.getId(),
                                RecoveryStrategy.CUSTOMER_ACTION_REQUIRED
                        )
        ).thenReturn(Optional.empty());

        RecoveryAction pendingAction =
                RecoveryAction.builder()
                        .id(3L)
                        .recoveryCase(recoveryCase)
                        .strategy(
                                RecoveryStrategy.CUSTOMER_ACTION_REQUIRED
                        )
                        .priority(
                                RecoveryPriority.MEDIUM
                        )
                        .recoveryScore(
                                new BigDecimal("0.60")
                        )
                        .status(
                                RecoveryAction.ActionStatus.PENDING
                        )
                        .reason(
                                "Customer authentication required"
                        )
                        .build();

        when(
                recoveryActionRepository.save(
                        any(RecoveryAction.class)
                )
        ).thenReturn(pendingAction);

        RecoveryOutcome outcome =
                new RecoveryOutcome(
                        RecoveryOutcome.OutcomeStatus.FAILED,
                        BigDecimal.ZERO,
                        "Customer action is required before payment can be recovered."
                );

        when(
                customerActionHandler.handle(
                        recoveryCase,
                        decision
                )
        ).thenReturn(outcome);

        recoveryActionExecutor.execute(
                recoveryCase,
                decision
        );

        verify(
                recoveryActionRepository,
                times(2)
        ).save(any(RecoveryAction.class));

        verify(
                customerActionHandler,
                times(1)
        ).handle(
                recoveryCase,
                decision
        );

        verify(
                retryPaymentHandler,
                never()
        ).handle(any(), any());

        verify(
                updatePaymentMethodHandler,
                never()
        ).handle(any(), any());

        verify(
                manualReviewHandler,
                never()
        ).handle(any(), any());

        assertEquals(
                RecoveryAction.ActionStatus.EXECUTED,
                pendingAction.getStatus()
        );

        assertNotNull(
                pendingAction.getExecutedAt()
        );
    }

    // ============================================================
    // MANUAL REVIEW
    // ============================================================

    @Test
    void shouldExecuteManualReviewStrategy() {

        RecoveryCase recoveryCase =
                RecoveryCase.builder()
                        .id(UUID.randomUUID())
                        .build();

        RecoveryDecision decision =
                RecoveryDecision.builder()
                        .strategy(
                                RecoveryStrategy.MANUAL_REVIEW
                        )
                        .priority(
                                RecoveryPriority.HIGH
                        )
                        .recoveryScore(
                                new BigDecimal("0.30")
                        )
                        .reason(
                                "Recovery requires manual review"
                        )
                        .build();

        approveDecision(
                recoveryCase,
                decision
        );

        when(
                recoveryActionRepository
                        .findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                                recoveryCase.getId(),
                                RecoveryStrategy.MANUAL_REVIEW
                        )
        ).thenReturn(Optional.empty());

        RecoveryAction pendingAction =
                RecoveryAction.builder()
                        .id(4L)
                        .recoveryCase(recoveryCase)
                        .strategy(
                                RecoveryStrategy.MANUAL_REVIEW
                        )
                        .priority(
                                RecoveryPriority.HIGH
                        )
                        .recoveryScore(
                                new BigDecimal("0.30")
                        )
                        .status(
                                RecoveryAction.ActionStatus.PENDING
                        )
                        .reason(
                                "Recovery requires manual review"
                        )
                        .build();

        when(
                recoveryActionRepository.save(
                        any(RecoveryAction.class)
                )
        ).thenReturn(pendingAction);

        RecoveryOutcome outcome =
                new RecoveryOutcome(
                        RecoveryOutcome.OutcomeStatus.FAILED,
                        BigDecimal.ZERO,
                        "Recovery case requires manual review."
                );

        when(
                manualReviewHandler.handle(
                        recoveryCase,
                        decision
                )
        ).thenReturn(outcome);

        recoveryActionExecutor.execute(
                recoveryCase,
                decision
        );

        verify(
                recoveryActionRepository,
                times(2)
        ).save(any(RecoveryAction.class));

        verify(
                manualReviewHandler,
                times(1)
        ).handle(
                recoveryCase,
                decision
        );

        verify(
                retryPaymentHandler,
                never()
        ).handle(any(), any());

        verify(
                updatePaymentMethodHandler,
                never()
        ).handle(any(), any());

        verify(
                customerActionHandler,
                never()
        ).handle(any(), any());

        assertEquals(
                RecoveryAction.ActionStatus.EXECUTED,
                pendingAction.getStatus()
        );

        assertNotNull(
                pendingAction.getExecutedAt()
        );
    }

    // ============================================================
    // NULL RECOVERY CASE
    // ============================================================

    @Test
    void shouldRejectNullRecoveryCase() {

        RecoveryDecision decision =
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
                                "Test decision"
                        )
                        .build();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        recoveryActionExecutor.execute(
                                null,
                                decision
                        )
        );

        verifyNoInteractions(
                recoveryActionRepository,
                recoveryDecisionGuard
        );
    }

    // ============================================================
    // NULL DECISION
    // ============================================================

    @Test
    void shouldRejectNullRecoveryDecision() {

        RecoveryCase recoveryCase =
                RecoveryCase.builder()
                        .id(UUID.randomUUID())
                        .build();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        recoveryActionExecutor.execute(
                                recoveryCase,
                                null
                        )
        );

        verifyNoInteractions(
                recoveryActionRepository,
                recoveryDecisionGuard
        );
    }

    // ============================================================
    // NULL STRATEGY
    // ============================================================

    @Test
    void shouldNotCreateActionWhenStrategyIsNull() {

        RecoveryCase recoveryCase =
                RecoveryCase.builder()
                        .id(UUID.randomUUID())
                        .build();

        RecoveryDecision decision =
                RecoveryDecision.builder()
                        .strategy(null)
                        .priority(
                                RecoveryPriority.MEDIUM
                        )
                        .recoveryScore(
                                new BigDecimal("0.50")
                        )
                        .reason(
                                "No recovery strategy available"
                        )
                        .build();

        RecoveryDecisionGuard.GuardResult guardResult =
                RecoveryDecisionGuard.GuardResult.builder()
                        .allowed(false)
                        .reason(
                                "Recovery decision does not contain a strategy"
                        )
                        .build();

        when(
                recoveryDecisionGuard.validate(
                        recoveryCase,
                        decision
                )
        ).thenReturn(guardResult);

        recoveryActionExecutor.execute(
                recoveryCase,
                decision
        );

        verify(
                recoveryDecisionGuard,
                times(1)
        ).validate(
                recoveryCase,
                decision
        );

        verifyNoInteractions(
                recoveryActionRepository
        );

        verify(
                retryPaymentHandler,
                never()
        ).handle(any(), any());

        verify(
                updatePaymentMethodHandler,
                never()
        ).handle(any(), any());

        verify(
                customerActionHandler,
                never()
        ).handle(any(), any());

        verify(
                manualReviewHandler,
                never()
        ).handle(any(), any());
    }

    // ============================================================
    // GUARD REJECTS DECISION
    // ============================================================

    @Test
    void shouldNotExecuteWhenDecisionGuardRejects() {

        RecoveryCase recoveryCase =
                RecoveryCase.builder()
                        .id(UUID.randomUUID())
                        .build();

        RecoveryDecision decision =
                RecoveryDecision.builder()
                        .strategy(
                                RecoveryStrategy.RETRY_PAYMENT
                        )
                        .priority(
                                RecoveryPriority.MEDIUM
                        )
                        .recoveryScore(
                                new BigDecimal("0.20")
                        )
                        .reason(
                                "Low recovery confidence"
                        )
                        .build();

        RecoveryDecisionGuard.GuardResult guardResult =
                RecoveryDecisionGuard.GuardResult.builder()
                        .allowed(false)
                        .reason(
                                "Recovery score is below the minimum automation threshold"
                        )
                        .build();

        when(
                recoveryDecisionGuard.validate(
                        recoveryCase,
                        decision
                )
        ).thenReturn(guardResult);

        recoveryActionExecutor.execute(
                recoveryCase,
                decision
        );

        verify(
                recoveryDecisionGuard,
                times(1)
        ).validate(
                recoveryCase,
                decision
        );

        verifyNoInteractions(
                recoveryActionRepository
        );

        verify(
                retryPaymentHandler,
                never()
        ).handle(any(), any());

        verify(
                updatePaymentMethodHandler,
                never()
        ).handle(any(), any());

        verify(
                customerActionHandler,
                never()
        ).handle(any(), any());

        verify(
                manualReviewHandler,
                never()
        ).handle(any(), any());
    }

    // ============================================================
    // EXISTING EXECUTED ACTION
    // ============================================================

    @Test
    void shouldSkipAlreadyExecutedAction() {

        RecoveryCase recoveryCase =
                RecoveryCase.builder()
                        .id(UUID.randomUUID())
                        .build();

        RecoveryDecision decision =
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
                                "Payment failed"
                        )
                        .build();

        approveDecision(
                recoveryCase,
                decision
        );

        RecoveryAction executedAction =
                RecoveryAction.builder()
                        .id(10L)
                        .recoveryCase(recoveryCase)
                        .strategy(
                                RecoveryStrategy.RETRY_PAYMENT
                        )
                        .priority(
                                RecoveryPriority.MEDIUM_HIGH
                        )
                        .recoveryScore(
                                new BigDecimal("0.70")
                        )
                        .status(
                                RecoveryAction.ActionStatus.EXECUTED
                        )
                        .reason(
                                "Payment failed"
                        )
                        .build();

        when(
                recoveryActionRepository
                        .findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                                recoveryCase.getId(),
                                RecoveryStrategy.RETRY_PAYMENT
                        )
        ).thenReturn(
                Optional.of(executedAction)
        );

        recoveryActionExecutor.execute(
                recoveryCase,
                decision
        );

        verify(
                recoveryDecisionGuard,
                times(1)
        ).validate(
                recoveryCase,
                decision
        );

        verify(
                recoveryActionRepository,
                times(1)
        ).findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                recoveryCase.getId(),
                RecoveryStrategy.RETRY_PAYMENT
        );

        verify(
                recoveryActionRepository,
                never()
        ).save(any(RecoveryAction.class));

        verify(
                retryPaymentHandler,
                never()
        ).handle(any(), any());
    }

    // ============================================================
    // EXISTING PENDING ACTION
    // ============================================================

    @Test
    void shouldSkipAlreadyPendingAction() {

        RecoveryCase recoveryCase =
                RecoveryCase.builder()
                        .id(UUID.randomUUID())
                        .build();

        RecoveryDecision decision =
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
                                "Payment failed"
                        )
                        .build();

        approveDecision(
                recoveryCase,
                decision
        );

        RecoveryAction pendingAction =
                RecoveryAction.builder()
                        .id(11L)
                        .recoveryCase(recoveryCase)
                        .strategy(
                                RecoveryStrategy.RETRY_PAYMENT
                        )
                        .priority(
                                RecoveryPriority.MEDIUM_HIGH
                        )
                        .recoveryScore(
                                new BigDecimal("0.70")
                        )
                        .status(
                                RecoveryAction.ActionStatus.PENDING
                        )
                        .reason(
                                "Payment failed"
                        )
                        .build();

        when(
                recoveryActionRepository
                        .findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                                recoveryCase.getId(),
                                RecoveryStrategy.RETRY_PAYMENT
                        )
        ).thenReturn(
                Optional.of(pendingAction)
        );

        recoveryActionExecutor.execute(
                recoveryCase,
                decision
        );

        verify(
                recoveryDecisionGuard,
                times(1)
        ).validate(
                recoveryCase,
                decision
        );

        verify(
                recoveryActionRepository,
                times(1)
        ).findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                recoveryCase.getId(),
                RecoveryStrategy.RETRY_PAYMENT
        );

        verify(
                recoveryActionRepository,
                never()
        ).save(any(RecoveryAction.class));

        verify(
                retryPaymentHandler,
                never()
        ).handle(any(), any());
    }

    // ============================================================
    // FAILED ACTION CAN BE RETRIED
    // ============================================================

    @Test
    void shouldCreateNewActionWhenPreviousActionFailed() {

        RecoveryCase recoveryCase =
                RecoveryCase.builder()
                        .id(UUID.randomUUID())
                        .build();

        RecoveryDecision decision =
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
                                "Retry payment"
                        )
                        .build();

        approveDecision(
                recoveryCase,
                decision
        );

        RecoveryAction failedAction =
                RecoveryAction.builder()
                        .id(20L)
                        .recoveryCase(recoveryCase)
                        .strategy(
                                RecoveryStrategy.RETRY_PAYMENT
                        )
                        .priority(
                                RecoveryPriority.MEDIUM_HIGH
                        )
                        .recoveryScore(
                                new BigDecimal("0.70")
                        )
                        .status(
                                RecoveryAction.ActionStatus.FAILED
                        )
                        .reason(
                                "Previous retry failed"
                        )
                        .build();

        when(
                recoveryActionRepository
                        .findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                                recoveryCase.getId(),
                                RecoveryStrategy.RETRY_PAYMENT
                        )
        ).thenReturn(
                Optional.of(failedAction)
        );

        RecoveryAction newPendingAction =
                RecoveryAction.builder()
                        .id(21L)
                        .recoveryCase(recoveryCase)
                        .strategy(
                                RecoveryStrategy.RETRY_PAYMENT
                        )
                        .priority(
                                RecoveryPriority.MEDIUM_HIGH
                        )
                        .recoveryScore(
                                new BigDecimal("0.70")
                        )
                        .status(
                                RecoveryAction.ActionStatus.PENDING
                        )
                        .reason(
                                "Retry payment"
                        )
                        .build();

        when(
                recoveryActionRepository.save(
                        any(RecoveryAction.class)
                )
        ).thenReturn(
                newPendingAction
        );

        RecoveryOutcome outcome =
                new RecoveryOutcome(
                        RecoveryOutcome.OutcomeStatus.FAILED,
                        BigDecimal.ZERO,
                        "Payment retry request has not yet been integrated with Razorpay."
                );

        when(
                retryPaymentHandler.handle(
                        recoveryCase,
                        decision
                )
        ).thenReturn(outcome);

        recoveryActionExecutor.execute(
                recoveryCase,
                decision
        );

        verify(
                recoveryActionRepository,
                times(2)
        ).save(any(RecoveryAction.class));

        verify(
                retryPaymentHandler,
                times(1)
        ).handle(
                recoveryCase,
                decision
        );

        assertEquals(
                RecoveryAction.ActionStatus.EXECUTED,
                newPendingAction.getStatus()
        );

        assertNotNull(
                newPendingAction.getExecutedAt()
        );
    }

    // ============================================================
    // HELPER
    // ============================================================

    private void approveDecision(
            RecoveryCase recoveryCase,
            RecoveryDecision decision
    ) {

        RecoveryDecisionGuard.GuardResult guardResult =
                RecoveryDecisionGuard.GuardResult.builder()
                        .allowed(true)
                        .reason(
                                "Recovery decision passed all safety checks"
                        )
                        .build();

        when(
                recoveryDecisionGuard.validate(
                        recoveryCase,
                        decision
                )
        ).thenReturn(guardResult);
    }
}