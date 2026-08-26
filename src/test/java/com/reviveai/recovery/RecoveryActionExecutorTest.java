package com.reviveai.recovery;

import com.reviveai.entity.RecoveryAction;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.repository.RecoveryActionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecoveryActionExecutorTest {

    @Mock
    private RecoveryActionRepository recoveryActionRepository;

    @InjectMocks
    private RecoveryActionExecutor recoveryActionExecutor;


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

        RecoveryAction executedAction =
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
                                RecoveryAction.ActionStatus.EXECUTED
                        )
                        .reason(
                                "Payment failed with insufficient funds"
                        )
                        .build();

        when(
                recoveryActionRepository.save(
                        any(RecoveryAction.class)
                )
        )
                .thenReturn(
                        pendingAction,
                        executedAction
                );

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
        ).save(
                captor.capture()
        );

        RecoveryAction firstSavedAction =
                captor.getAllValues().get(0);

        RecoveryAction secondSavedAction =
                captor.getAllValues().get(1);

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
    }


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

        RecoveryAction executedAction =
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
                                RecoveryAction.ActionStatus.EXECUTED
                        )
                        .reason(
                                "Card expired"
                        )
                        .build();

        when(
                recoveryActionRepository.save(
                        any(RecoveryAction.class)
                )
        )
                .thenReturn(
                        pendingAction,
                        executedAction
                );

        recoveryActionExecutor.execute(
                recoveryCase,
                decision
        );

        verify(
                recoveryActionRepository,
                times(2)
        ).save(
                any(RecoveryAction.class)
        );

        assertEquals(
                RecoveryAction.ActionStatus.EXECUTED,
                executedAction.getStatus()
        );
    }


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

        RecoveryAction executedAction =
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
                                RecoveryAction.ActionStatus.EXECUTED
                        )
                        .reason(
                                "Customer authentication required"
                        )
                        .build();

        when(
                recoveryActionRepository.save(
                        any(RecoveryAction.class)
                )
        )
                .thenReturn(
                        pendingAction,
                        executedAction
                );

        recoveryActionExecutor.execute(
                recoveryCase,
                decision
        );

        verify(
                recoveryActionRepository,
                times(2)
        ).save(
                any(RecoveryAction.class)
        );

        assertEquals(
                RecoveryAction.ActionStatus.EXECUTED,
                executedAction.getStatus()
        );
    }


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

        RecoveryAction executedAction =
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
                                RecoveryAction.ActionStatus.EXECUTED
                        )
                        .reason(
                                "Recovery requires manual review"
                        )
                        .build();

        when(
                recoveryActionRepository.save(
                        any(RecoveryAction.class)
                )
        )
                .thenReturn(
                        pendingAction,
                        executedAction
                );

        recoveryActionExecutor.execute(
                recoveryCase,
                decision
        );

        verify(
                recoveryActionRepository,
                times(2)
        ).save(
                any(RecoveryAction.class)
        );

        assertEquals(
                RecoveryAction.ActionStatus.EXECUTED,
                executedAction.getStatus()
        );
    }


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
                recoveryActionRepository
        );
    }


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
                recoveryActionRepository
        );
    }


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

        recoveryActionExecutor.execute(
                recoveryCase,
                decision
        );

        verifyNoInteractions(
                recoveryActionRepository
        );
    }
}

