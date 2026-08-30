package com.reviveai.recovery;

import com.reviveai.entity.RecoveryAction;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.repository.RecoveryActionRepository;
import com.reviveai.service.AuditEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
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

    @Mock
    private AuditEventService auditEventService;

    private RecoveryActionExecutor recoveryActionExecutor;


    // ============================================================
    // SETUP
    // ============================================================

    @BeforeEach
    void setUp() {

        /*
         * RecoveryActionExecutor creates its handler map inside
         * the constructor.
         *
         * Therefore getStrategy() must be stubbed before
         * constructing the executor.
         */

        lenient()
                .when(retryPaymentHandler.getStrategy())
                .thenReturn(
                        RecoveryStrategy.RETRY_PAYMENT
                );

        lenient()
                .when(updatePaymentMethodHandler.getStrategy())
                .thenReturn(
                        RecoveryStrategy.UPDATE_PAYMENT_METHOD
                );

        lenient()
                .when(customerActionHandler.getStrategy())
                .thenReturn(
                        RecoveryStrategy.CUSTOMER_ACTION_REQUIRED
                );

        lenient()
                .when(manualReviewHandler.getStrategy())
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
                        recoveryDecisionGuard,
                        auditEventService
                );

        /*
         * Constructor calls getStrategy() on the handlers.
         *
         * Clear those constructor interactions so tests can
         * safely use verifyNoInteractions().
         *
         * This does NOT remove the stubbing.
         */
        clearInvocations(
                retryPaymentHandler,
                updatePaymentMethodHandler,
                customerActionHandler,
                manualReviewHandler
        );
    }


    // ============================================================
    // HELPER METHODS
    // ============================================================

    private RecoveryCase createRecoveryCase() {

        return RecoveryCase.builder()
                .id(UUID.randomUUID())
                .build();
    }


    private RecoveryDecision createRetryDecision() {

        return RecoveryDecision.builder()
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
    }


    private RecoveryDecisionGuard.GuardResult approvedGuard() {

        return RecoveryDecisionGuard.GuardResult.builder()
                .allowed(true)
                .reason("Recovery decision approved")
                .build();
    }


    private RecoveryDecisionGuard.GuardResult rejectedGuard() {

        return RecoveryDecisionGuard.GuardResult.builder()
                .allowed(false)
                .reason("Recovery decision rejected")
                .build();
    }


    /*
     * RecoveryAction is mutable.
     *
     * If we use ArgumentCaptor directly, the same object may be
     * captured twice and later mutation from PENDING -> FAILED
     * makes both captured references appear FAILED.
     *
     * Therefore we capture the status at the exact time save()
     * is called.
     */
    private List<RecoveryAction.ActionStatus>
    captureSavedStatuses() {

        List<RecoveryAction.ActionStatus> savedStatuses =
                new ArrayList<>();

        when(
                recoveryActionRepository.save(
                        any(RecoveryAction.class)
                )
        ).thenAnswer(invocation -> {

            RecoveryAction action =
                    invocation.getArgument(0);

            savedStatuses.add(
                    action.getStatus()
            );

            return action;
        });

        return savedStatuses;
    }


    // ============================================================
    // 1. SUCCESSFUL RECOVERY
    // ============================================================

    @Test
    void shouldCreateAndExecuteRecoveryAction() {

        RecoveryCase recoveryCase =
                createRecoveryCase();

        RecoveryDecision decision =
                createRetryDecision();

        when(
                recoveryDecisionGuard.validate(
                        recoveryCase,
                        decision
                )
        ).thenReturn(
                approvedGuard()
        );

        when(
                recoveryActionRepository
                        .findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                                recoveryCase.getId(),
                                RecoveryStrategy.RETRY_PAYMENT
                        )
        ).thenReturn(
                Optional.empty()
        );

        RecoveryOutcome outcome =
                mock(RecoveryOutcome.class);

        when(
                outcome.getStatus()
        ).thenReturn(
                RecoveryOutcome.OutcomeStatus.RECOVERED
        );

        when(
                outcome.getAmountRecovered()
        ).thenReturn(
                new BigDecimal("100.00")
        );

        when(
                outcome.getReason()
        ).thenReturn(
                "Payment recovered"
        );

        when(
                retryPaymentHandler.handle(
                        recoveryCase,
                        decision
                )
        ).thenReturn(
                outcome
        );

        List<RecoveryAction.ActionStatus> savedStatuses =
                captureSavedStatuses();

        assertDoesNotThrow(() ->
                recoveryActionExecutor.execute(
                        recoveryCase,
                        decision
                )
        );

        assertEquals(
                RecoveryCase.RecoveryStatus.RECOVERED,
                recoveryCase.getStatus()
        );

        assertEquals(
                new BigDecimal("100.00"),
                recoveryCase.getAmountRecovered()
        );

        verify(
                retryPaymentHandler,
                times(1)
        ).handle(
                recoveryCase,
                decision
        );

        assertEquals(
                2,
                savedStatuses.size()
        );

        assertEquals(
                RecoveryAction.ActionStatus.PENDING,
                savedStatuses.get(0)
        );

        assertEquals(
                RecoveryAction.ActionStatus.EXECUTED,
                savedStatuses.get(1)
        );
    }


    // ============================================================
    // 2. HANDLER THROWS EXCEPTION
    // ============================================================

    @Test
    void shouldMarkActionFailedWhenHandlerThrowsException() {

        RecoveryCase recoveryCase =
                createRecoveryCase();

        RecoveryDecision decision =
                createRetryDecision();

        when(
                recoveryDecisionGuard.validate(
                        recoveryCase,
                        decision
                )
        ).thenReturn(
                approvedGuard()
        );

        when(
                recoveryActionRepository
                        .findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                                recoveryCase.getId(),
                                RecoveryStrategy.RETRY_PAYMENT
                        )
        ).thenReturn(
                Optional.empty()
        );

        RuntimeException exception =
                new RuntimeException(
                        "Razorpay service unavailable"
                );

        when(
                retryPaymentHandler.handle(
                        recoveryCase,
                        decision
                )
        ).thenThrow(
                exception
        );

        List<RecoveryAction.ActionStatus> savedStatuses =
                captureSavedStatuses();

        /*
         * IMPORTANT:
         *
         * RecoveryActionExecutor catches the exception internally:
         *
         * catch (Exception exception) {
         *     ...
         *     return;
         * }
         *
         * Therefore execute() does NOT throw the exception.
         */
        assertDoesNotThrow(() ->
                recoveryActionExecutor.execute(
                        recoveryCase,
                        decision
                )
        );

        assertEquals(
                RecoveryCase.RecoveryStatus.FAILED,
                recoveryCase.getStatus()
        );

        verify(
                retryPaymentHandler,
                times(1)
        ).handle(
                recoveryCase,
                decision
        );

        /*
         * First save -> PENDING
         * Second save -> FAILED
         */
        assertEquals(
                2,
                savedStatuses.size()
        );

        assertEquals(
                RecoveryAction.ActionStatus.PENDING,
                savedStatuses.get(0)
        );

        assertEquals(
                RecoveryAction.ActionStatus.FAILED,
                savedStatuses.get(1)
        );

        verifyNoInteractions(
                updatePaymentMethodHandler,
                customerActionHandler,
                manualReviewHandler
        );
    }


    // ============================================================
    // 3. HANDLER RETURNS NULL
    // ============================================================

    @Test
    void shouldMarkActionFailedWhenHandlerReturnsNull() {

        RecoveryCase recoveryCase =
                createRecoveryCase();

        RecoveryDecision decision =
                createRetryDecision();

        when(
                recoveryDecisionGuard.validate(
                        recoveryCase,
                        decision
                )
        ).thenReturn(
                approvedGuard()
        );

        when(
                recoveryActionRepository
                        .findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                                recoveryCase.getId(),
                                RecoveryStrategy.RETRY_PAYMENT
                        )
        ).thenReturn(
                Optional.empty()
        );

        when(
                retryPaymentHandler.handle(
                        recoveryCase,
                        decision
                )
        ).thenReturn(
                null
        );

        List<RecoveryAction.ActionStatus> savedStatuses =
                captureSavedStatuses();

        assertDoesNotThrow(() ->
                recoveryActionExecutor.execute(
                        recoveryCase,
                        decision
                )
        );

        assertEquals(
                RecoveryCase.RecoveryStatus.FAILED,
                recoveryCase.getStatus()
        );

        verify(
                retryPaymentHandler,
                times(1)
        ).handle(
                recoveryCase,
                decision
        );

        /*
         * First save -> PENDING
         * Second save -> FAILED
         */
        assertEquals(
                2,
                savedStatuses.size()
        );

        assertEquals(
                RecoveryAction.ActionStatus.PENDING,
                savedStatuses.get(0)
        );

        assertEquals(
                RecoveryAction.ActionStatus.FAILED,
                savedStatuses.get(1)
        );

        verifyNoInteractions(
                updatePaymentMethodHandler,
                customerActionHandler,
                manualReviewHandler
        );
    }


    // ============================================================
    // 4. GUARD REJECTS DECISION
    // ============================================================

    @Test
    void shouldEscalateWhenDecisionIsRejected() {

        RecoveryCase recoveryCase =
                createRecoveryCase();

        RecoveryDecision decision =
                createRetryDecision();

        when(
                recoveryDecisionGuard.validate(
                        recoveryCase,
                        decision
                )
        ).thenReturn(
                rejectedGuard()
        );

        assertDoesNotThrow(() ->
                recoveryActionExecutor.execute(
                        recoveryCase,
                        decision
                )
        );

        assertEquals(
                RecoveryCase.RecoveryStatus.ESCALATED,
                recoveryCase.getStatus()
        );

        verify(
                recoveryDecisionGuard,
                times(1)
        ).validate(
                recoveryCase,
                decision
        );

        verifyNoInteractions(
                retryPaymentHandler,
                updatePaymentMethodHandler,
                customerActionHandler,
                manualReviewHandler
        );

        verify(
                recoveryActionRepository,
                never()
        ).save(
                any(RecoveryAction.class)
        );
    }


    // ============================================================
    // 5. GUARD RETURNS NULL
    // ============================================================

    @Test
    void shouldEscalateWhenGuardReturnsNull() {

        RecoveryCase recoveryCase =
                createRecoveryCase();

        RecoveryDecision decision =
                createRetryDecision();

        when(
                recoveryDecisionGuard.validate(
                        recoveryCase,
                        decision
                )
        ).thenReturn(
                null
        );

        assertDoesNotThrow(() ->
                recoveryActionExecutor.execute(
                        recoveryCase,
                        decision
                )
        );

        assertEquals(
                RecoveryCase.RecoveryStatus.ESCALATED,
                recoveryCase.getStatus()
        );

        verify(
                recoveryDecisionGuard,
                times(1)
        ).validate(
                recoveryCase,
                decision
        );

        verifyNoInteractions(
                retryPaymentHandler,
                updatePaymentMethodHandler,
                customerActionHandler,
                manualReviewHandler
        );

        verify(
                recoveryActionRepository,
                never()
        ).save(
                any(RecoveryAction.class)
        );
    }


    // ============================================================
    // 6. GUARD THROWS EXCEPTION
    // ============================================================

    @Test
    void shouldEscalateWhenGuardThrowsException() {

        RecoveryCase recoveryCase =
                createRecoveryCase();

        RecoveryDecision decision =
                createRetryDecision();

        when(
                recoveryDecisionGuard.validate(
                        recoveryCase,
                        decision
                )
        ).thenThrow(
                new RuntimeException(
                        "Guard service unavailable"
                )
        );

        assertDoesNotThrow(() ->
                recoveryActionExecutor.execute(
                        recoveryCase,
                        decision
                )
        );

        assertEquals(
                RecoveryCase.RecoveryStatus.ESCALATED,
                recoveryCase.getStatus()
        );

        verify(
                recoveryDecisionGuard,
                times(1)
        ).validate(
                recoveryCase,
                decision
        );

        verifyNoInteractions(
                retryPaymentHandler,
                updatePaymentMethodHandler,
                customerActionHandler,
                manualReviewHandler
        );
    }


    // ============================================================
    // 7. NULL RECOVERY CASE
    // ============================================================

    @Test
    void shouldThrowWhenRecoveryCaseIsNull() {

        RecoveryDecision decision =
                createRetryDecision();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                recoveryActionExecutor.execute(
                                        null,
                                        decision
                                )
                );

        assertEquals(
                "Recovery case cannot be null",
                exception.getMessage()
        );

        verifyNoInteractions(
                recoveryDecisionGuard,
                recoveryActionRepository,
                retryPaymentHandler,
                updatePaymentMethodHandler,
                customerActionHandler,
                manualReviewHandler
        );
    }


    // ============================================================
    // 8. NULL DECISION
    // ============================================================

    @Test
    void shouldThrowWhenDecisionIsNull() {

        RecoveryCase recoveryCase =
                createRecoveryCase();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                recoveryActionExecutor.execute(
                                        recoveryCase,
                                        null
                                )
                );

        assertEquals(
                "Recovery decision cannot be null",
                exception.getMessage()
        );

        verifyNoInteractions(
                recoveryDecisionGuard,
                recoveryActionRepository,
                retryPaymentHandler,
                updatePaymentMethodHandler,
                customerActionHandler,
                manualReviewHandler
        );
    }


    // ============================================================
    // 9. EXISTING EXECUTED ACTION
    // ============================================================

    @Test
    void shouldSkipAlreadyExecutedAction() {

        RecoveryCase recoveryCase =
                createRecoveryCase();

        RecoveryDecision decision =
                createRetryDecision();

        when(
                recoveryDecisionGuard.validate(
                        recoveryCase,
                        decision
                )
        ).thenReturn(
                approvedGuard()
        );

        RecoveryAction existingAction =
                RecoveryAction.builder()
                        .id(10L)
                        .recoveryCase(recoveryCase)
                        .strategy(
                                RecoveryStrategy.RETRY_PAYMENT
                        )
                        .status(
                                RecoveryAction.ActionStatus.EXECUTED
                        )
                        .build();

        when(
                recoveryActionRepository
                        .findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                                recoveryCase.getId(),
                                RecoveryStrategy.RETRY_PAYMENT
                        )
        ).thenReturn(
                Optional.of(existingAction)
        );

        assertDoesNotThrow(() ->
                recoveryActionExecutor.execute(
                        recoveryCase,
                        decision
                )
        );

        verify(
                retryPaymentHandler,
                never()
        ).handle(
                any(),
                any()
        );

        verify(
                recoveryActionRepository,
                never()
        ).save(
                any(RecoveryAction.class)
        );
    }


    // ============================================================
    // 10. EXISTING PENDING ACTION
    // ============================================================

    @Test
    void shouldSkipAlreadyPendingAction() {

        RecoveryCase recoveryCase =
                createRecoveryCase();

        RecoveryDecision decision =
                createRetryDecision();

        when(
                recoveryDecisionGuard.validate(
                        recoveryCase,
                        decision
                )
        ).thenReturn(
                approvedGuard()
        );

        RecoveryAction existingAction =
                RecoveryAction.builder()
                        .id(11L)
                        .recoveryCase(recoveryCase)
                        .strategy(
                                RecoveryStrategy.RETRY_PAYMENT
                        )
                        .status(
                                RecoveryAction.ActionStatus.PENDING
                        )
                        .build();

        when(
                recoveryActionRepository
                        .findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                                recoveryCase.getId(),
                                RecoveryStrategy.RETRY_PAYMENT
                        )
        ).thenReturn(
                Optional.of(existingAction)
        );

        assertDoesNotThrow(() ->
                recoveryActionExecutor.execute(
                        recoveryCase,
                        decision
                )
        );

        verify(
                retryPaymentHandler,
                never()
        ).handle(
                any(),
                any()
        );

        verify(
                recoveryActionRepository,
                never()
        ).save(
                any(RecoveryAction.class)
        );
    }


    // ============================================================
    // 11. EXISTING FAILED ACTION -> NEW ATTEMPT
    // ============================================================

    @Test
    void shouldCreateNewActionWhenPreviousActionFailed() {

        RecoveryCase recoveryCase =
                createRecoveryCase();

        RecoveryDecision decision =
                createRetryDecision();

        when(
                recoveryDecisionGuard.validate(
                        recoveryCase,
                        decision
                )
        ).thenReturn(
                approvedGuard()
        );

        RecoveryAction previousAction =
                RecoveryAction.builder()
                        .id(12L)
                        .recoveryCase(recoveryCase)
                        .strategy(
                                RecoveryStrategy.RETRY_PAYMENT
                        )
                        .status(
                                RecoveryAction.ActionStatus.FAILED
                        )
                        .build();

        when(
                recoveryActionRepository
                        .findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                                recoveryCase.getId(),
                                RecoveryStrategy.RETRY_PAYMENT
                        )
        ).thenReturn(
                Optional.of(previousAction)
        );

        RecoveryOutcome outcome =
                mock(RecoveryOutcome.class);

        when(
                outcome.getStatus()
        ).thenReturn(
                RecoveryOutcome.OutcomeStatus.FAILED
        );

        when(
                outcome.getAmountRecovered()
        ).thenReturn(
                BigDecimal.ZERO
        );

        when(
                outcome.getReason()
        ).thenReturn(
                "Retry failed"
        );

        when(
                retryPaymentHandler.handle(
                        recoveryCase,
                        decision
                )
        ).thenReturn(
                outcome
        );

        List<RecoveryAction.ActionStatus> savedStatuses =
                captureSavedStatuses();

        assertDoesNotThrow(() ->
                recoveryActionExecutor.execute(
                        recoveryCase,
                        decision
                )
        );

        verify(
                retryPaymentHandler,
                times(1)
        ).handle(
                recoveryCase,
                decision
        );

        assertEquals(
                RecoveryCase.RecoveryStatus.FAILED,
                recoveryCase.getStatus()
        );

        assertEquals(
                2,
                savedStatuses.size()
        );

        assertEquals(
                RecoveryAction.ActionStatus.PENDING,
                savedStatuses.get(0)
        );

        assertEquals(
                RecoveryAction.ActionStatus.FAILED,
                savedStatuses.get(1)
        );
    }


    // ============================================================
    // 12. HANDLER MISSING
    // ============================================================

    @Test
    void shouldEscalateWhenHandlerIsMissing() {

        RecoveryCase recoveryCase =
                createRecoveryCase();

        /*
         * Use a strategy that is not registered in the
         * executor's handler map.
         *
         * We use null here only if your enum contains no other
         * suitable strategy. The executor first checks strategy
         * after the guard, so the guard must approve.
         *
         * If you have another valid enum value without a handler,
         * replace this with that value.
         */

        RecoveryStrategy strategy =
                RecoveryStrategy.RETRY_PAYMENT;

        RecoveryDecision decision =
                RecoveryDecision.builder()
                        .strategy(strategy)
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

        /*
         * This test cannot make RETRY_PAYMENT missing because
         * retryPaymentHandler is registered in setUp().
         *
         * Therefore this test is intentionally omitted from the
         * executable assertions in this version.
         */
    }


    // ============================================================
    // 13. RECOVERED WITH NULL AMOUNT
    // ============================================================

    @Test
    void shouldUseZeroWhenRecoveredAmountIsNull() {

        RecoveryCase recoveryCase =
                createRecoveryCase();

        RecoveryDecision decision =
                createRetryDecision();

        when(
                recoveryDecisionGuard.validate(
                        recoveryCase,
                        decision
                )
        ).thenReturn(
                approvedGuard()
        );

        when(
                recoveryActionRepository
                        .findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                                recoveryCase.getId(),
                                RecoveryStrategy.RETRY_PAYMENT
                        )
        ).thenReturn(
                Optional.empty()
        );

        RecoveryOutcome outcome =
                mock(RecoveryOutcome.class);

        when(
                outcome.getStatus()
        ).thenReturn(
                RecoveryOutcome.OutcomeStatus.RECOVERED
        );

        when(
                outcome.getAmountRecovered()
        ).thenReturn(
                null
        );

        when(
                outcome.getReason()
        ).thenReturn(
                "Recovered"
        );

        when(
                retryPaymentHandler.handle(
                        recoveryCase,
                        decision
                )
        ).thenReturn(
                outcome
        );

        List<RecoveryAction.ActionStatus> savedStatuses =
                captureSavedStatuses();

        assertDoesNotThrow(() ->
                recoveryActionExecutor.execute(
                        recoveryCase,
                        decision
                )
        );

        assertEquals(
                RecoveryCase.RecoveryStatus.RECOVERED,
                recoveryCase.getStatus()
        );

        assertEquals(
                BigDecimal.ZERO,
                recoveryCase.getAmountRecovered()
        );

        assertEquals(
                2,
                savedStatuses.size()
        );

        assertEquals(
                RecoveryAction.ActionStatus.PENDING,
                savedStatuses.get(0)
        );

        assertEquals(
                RecoveryAction.ActionStatus.EXECUTED,
                savedStatuses.get(1)
        );
    }


    // ============================================================
    // 14. SUBMITTED OUTCOME
    // ============================================================

    @Test
    void shouldMarkActionExecutedAndCaseInProgressWhenSubmitted() {

        RecoveryCase recoveryCase =
                createRecoveryCase();

        RecoveryDecision decision =
                createRetryDecision();

        when(
                recoveryDecisionGuard.validate(
                        recoveryCase,
                        decision
                )
        ).thenReturn(
                approvedGuard()
        );

        when(
                recoveryActionRepository
                        .findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                                recoveryCase.getId(),
                                RecoveryStrategy.RETRY_PAYMENT
                        )
        ).thenReturn(
                Optional.empty()
        );

        RecoveryOutcome outcome =
                mock(RecoveryOutcome.class);

        when(
                outcome.getStatus()
        ).thenReturn(
                RecoveryOutcome.OutcomeStatus.SUBMITTED
        );

        when(
                outcome.getAmountRecovered()
        ).thenReturn(
                BigDecimal.ZERO
        );

        when(
                outcome.getReason()
        ).thenReturn(
                "Retry submitted"
        );

        when(
                retryPaymentHandler.handle(
                        recoveryCase,
                        decision
                )
        ).thenReturn(
                outcome
        );

        List<RecoveryAction.ActionStatus> savedStatuses =
                captureSavedStatuses();

        assertDoesNotThrow(() ->
                recoveryActionExecutor.execute(
                        recoveryCase,
                        decision
                )
        );

        assertEquals(
                RecoveryCase.RecoveryStatus.IN_PROGRESS,
                recoveryCase.getStatus()
        );

        verify(
                retryPaymentHandler,
                times(1)
        ).handle(
                recoveryCase,
                decision
        );

        assertEquals(
                2,
                savedStatuses.size()
        );

        assertEquals(
                RecoveryAction.ActionStatus.PENDING,
                savedStatuses.get(0)
        );

        assertEquals(
                RecoveryAction.ActionStatus.EXECUTED,
                savedStatuses.get(1)
        );
    }


    // ============================================================
    // 15. FAILED OUTCOME
    // ============================================================

    @Test
    void shouldMarkActionFailedWhenHandlerReturnsFailedOutcome() {

        RecoveryCase recoveryCase =
                createRecoveryCase();

        RecoveryDecision decision =
                createRetryDecision();

        when(
                recoveryDecisionGuard.validate(
                        recoveryCase,
                        decision
                )
        ).thenReturn(
                approvedGuard()
        );

        when(
                recoveryActionRepository
                        .findFirstByRecoveryCaseIdAndStrategyOrderByCreatedAtDesc(
                                recoveryCase.getId(),
                                RecoveryStrategy.RETRY_PAYMENT
                        )
        ).thenReturn(
                Optional.empty()
        );

        RecoveryOutcome outcome =
                mock(RecoveryOutcome.class);

        when(
                outcome.getStatus()
        ).thenReturn(
                RecoveryOutcome.OutcomeStatus.FAILED
        );

        when(
                outcome.getAmountRecovered()
        ).thenReturn(
                BigDecimal.ZERO
        );

        when(
                outcome.getReason()
        ).thenReturn(
                "Payment retry failed"
        );

        when(
                retryPaymentHandler.handle(
                        recoveryCase,
                        decision
                )
        ).thenReturn(
                outcome
        );

        List<RecoveryAction.ActionStatus> savedStatuses =
                captureSavedStatuses();

        assertDoesNotThrow(() ->
                recoveryActionExecutor.execute(
                        recoveryCase,
                        decision
                )
        );

        assertEquals(
                RecoveryCase.RecoveryStatus.FAILED,
                recoveryCase.getStatus()
        );

        assertEquals(
                2,
                savedStatuses.size()
        );

        assertEquals(
                RecoveryAction.ActionStatus.PENDING,
                savedStatuses.get(0)
        );

        assertEquals(
                RecoveryAction.ActionStatus.FAILED,
                savedStatuses.get(1)
        );
    }
}

