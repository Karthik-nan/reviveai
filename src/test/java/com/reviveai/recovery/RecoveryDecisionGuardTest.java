package com.reviveai.recovery;

import com.reviveai.entity.RecoveryCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class RecoveryDecisionGuardTest {

    private RecoveryDecisionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new RecoveryDecisionGuard();
    }

    @Test
    void shouldApproveHighConfidenceAutomatedRecovery() {

        RecoveryCase recoveryCase = new RecoveryCase();

        RecoveryDecision decision =
                RecoveryDecision.builder()
                        .strategy(RecoveryStrategy.RETRY_PAYMENT)
                        .priority(RecoveryPriority.MEDIUM_HIGH)
                        .recoveryScore(new BigDecimal("0.70"))
                        .reason("Insufficient funds")
                        .build();

        RecoveryDecisionGuard.GuardResult result =
                guard.validate(
                        recoveryCase,
                        decision
                );

        assertTrue(result.isAllowed());
        assertEquals(
                "Recovery decision passed all safety checks",
                result.getReason()
        );
    }

    @Test
    void shouldApproveDecisionAtMinimumThreshold() {

        RecoveryCase recoveryCase = new RecoveryCase();

        RecoveryDecision decision =
                RecoveryDecision.builder()
                        .strategy(RecoveryStrategy.RETRY_PAYMENT)
                        .priority(RecoveryPriority.MEDIUM)
                        .recoveryScore(new BigDecimal("0.40"))
                        .reason("Retry allowed")
                        .build();

        RecoveryDecisionGuard.GuardResult result =
                guard.validate(
                        recoveryCase,
                        decision
                );

        assertTrue(result.isAllowed());
    }

    @Test
    void shouldRejectLowConfidenceAutomatedRecovery() {

        RecoveryCase recoveryCase = new RecoveryCase();

        RecoveryDecision decision =
                RecoveryDecision.builder()
                        .strategy(RecoveryStrategy.RETRY_PAYMENT)
                        .priority(RecoveryPriority.LOW)
                        .recoveryScore(new BigDecimal("0.20"))
                        .reason("Low confidence")
                        .build();

        RecoveryDecisionGuard.GuardResult result =
                guard.validate(
                        recoveryCase,
                        decision
                );

        assertFalse(result.isAllowed());

        assertEquals(
                "Recovery score is below the minimum automation threshold",
                result.getReason()
        );
    }

    @Test
    void shouldRejectMissingScoreForAutomatedRecovery() {

        RecoveryCase recoveryCase = new RecoveryCase();

        RecoveryDecision decision =
                RecoveryDecision.builder()
                        .strategy(RecoveryStrategy.RETRY_PAYMENT)
                        .priority(RecoveryPriority.MEDIUM)
                        .recoveryScore(null)
                        .reason("Missing score")
                        .build();

        RecoveryDecisionGuard.GuardResult result =
                guard.validate(
                        recoveryCase,
                        decision
                );

        assertFalse(result.isAllowed());

        assertEquals(
                "Automated recovery requires a recovery score",
                result.getReason()
        );
    }

    @Test
    void shouldApproveManualReviewWithoutScore() {

        RecoveryCase recoveryCase = new RecoveryCase();

        RecoveryDecision decision =
                RecoveryDecision.builder()
                        .strategy(RecoveryStrategy.MANUAL_REVIEW)
                        .priority(RecoveryPriority.LOW)
                        .recoveryScore(null)
                        .reason("Manual review required")
                        .build();

        RecoveryDecisionGuard.GuardResult result =
                guard.validate(
                        recoveryCase,
                        decision
                );

        assertTrue(result.isAllowed());

        assertEquals(
                "Manual review decision is allowed",
                result.getReason()
        );
    }

    @Test
    void shouldRejectInvalidScoreAboveOne() {

        RecoveryCase recoveryCase = new RecoveryCase();

        RecoveryDecision decision =
                RecoveryDecision.builder()
                        .strategy(RecoveryStrategy.RETRY_PAYMENT)
                        .priority(RecoveryPriority.HIGH)
                        .recoveryScore(new BigDecimal("1.20"))
                        .reason("Invalid score")
                        .build();

        RecoveryDecisionGuard.GuardResult result =
                guard.validate(
                        recoveryCase,
                        decision
                );

        assertFalse(result.isAllowed());

        assertEquals(
                "Recovery score must be between 0.00 and 1.00",
                result.getReason()
        );
    }

    @Test
    void shouldRejectInvalidNegativeScore() {

        RecoveryCase recoveryCase = new RecoveryCase();

        RecoveryDecision decision =
                RecoveryDecision.builder()
                        .strategy(RecoveryStrategy.RETRY_PAYMENT)
                        .priority(RecoveryPriority.LOW)
                        .recoveryScore(new BigDecimal("-0.10"))
                        .reason("Invalid score")
                        .build();

        RecoveryDecisionGuard.GuardResult result =
                guard.validate(
                        recoveryCase,
                        decision
                );

        assertFalse(result.isAllowed());

        assertEquals(
                "Recovery score must be between 0.00 and 1.00",
                result.getReason()
        );
    }

    @Test
    void shouldRejectMissingStrategy() {

        RecoveryCase recoveryCase = new RecoveryCase();

        RecoveryDecision decision =
                RecoveryDecision.builder()
                        .strategy(null)
                        .priority(RecoveryPriority.LOW)
                        .recoveryScore(new BigDecimal("0.70"))
                        .reason("Missing strategy")
                        .build();

        RecoveryDecisionGuard.GuardResult result =
                guard.validate(
                        recoveryCase,
                        decision
                );

        assertFalse(result.isAllowed());

        assertEquals(
                "Recovery decision does not contain a strategy",
                result.getReason()
        );
    }
}