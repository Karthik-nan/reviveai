package com.reviveai.recovery;
import com.reviveai.entity.PaymentAttempt;
import com.reviveai.entity.RecoveryCase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class RecoveryStrategyEngineTest {

    private final RecoveryStrategyEngine engine =
            new RecoveryStrategyEngine();

    @Test
    void shouldRetryPaymentForInsufficientFundsWithHighScore() {

        RecoveryCase recoveryCase = createCase(
                "INSUFFICIENT_FUNDS",
                "0.70"
        );

        RecoveryDecision decision =
                engine.determineStrategy(recoveryCase);

        assertEquals(
                RecoveryStrategy.RETRY_PAYMENT,
                decision.getStrategy()
        );

        assertEquals(
                RecoveryPriority.MEDIUM_HIGH,
                decision.getPriority()
        );

        assertEquals(
                new BigDecimal("0.70"),
                decision.getRecoveryScore()
        );

        assertTrue(
                decision.getReason()
                        .contains("INSUFFICIENT_FUNDS")
        );
    }

    @Test
    void shouldUpdatePaymentMethodForExpiredCard() {

        RecoveryCase recoveryCase = createCase(
                "CARD_EXPIRED",
                "0.80"
        );

        RecoveryDecision decision =
                engine.determineStrategy(recoveryCase);

        assertEquals(
                RecoveryStrategy.UPDATE_PAYMENT_METHOD,
                decision.getStrategy()
        );

        assertEquals(
                RecoveryPriority.HIGH,
                decision.getPriority()
        );

        assertEquals(
                new BigDecimal("0.80"),
                decision.getRecoveryScore()
        );
    }

    @Test
    void shouldRetryPaymentForCardDeclinedWithMediumScore() {

        RecoveryCase recoveryCase = createCase(
                "CARD_DECLINED",
                "0.50"
        );

        RecoveryDecision decision =
                engine.determineStrategy(recoveryCase);

        assertEquals(
                RecoveryStrategy.RETRY_PAYMENT,
                decision.getStrategy()
        );

        assertEquals(
                RecoveryPriority.MEDIUM,
                decision.getPriority()
        );

        assertEquals(
                new BigDecimal("0.50"),
                decision.getRecoveryScore()
        );
    }

    @Test
    void shouldRequireCustomerActionForAuthenticationFailure() {

        RecoveryCase recoveryCase = createCase(
                "AUTHENTICATION_FAILED",
                "0.40"
        );

        RecoveryDecision decision =
                engine.determineStrategy(recoveryCase);

        assertEquals(
                RecoveryStrategy.CUSTOMER_ACTION_REQUIRED,
                decision.getStrategy()
        );

        assertEquals(
                RecoveryPriority.MEDIUM,
                decision.getPriority()
        );

        assertEquals(
                new BigDecimal("0.40"),
                decision.getRecoveryScore()
        );
    }

    @Test
    void shouldManuallyReviewUnknownError() {

        RecoveryCase recoveryCase = createCase(
                "UNKNOWN_ERROR",
                "0.50"
        );

        RecoveryDecision decision =
                engine.determineStrategy(recoveryCase);

        assertEquals(
                RecoveryStrategy.MANUAL_REVIEW,
                decision.getStrategy()
        );

        assertEquals(
                RecoveryPriority.MEDIUM,
                decision.getPriority()
        );

        assertEquals(
                new BigDecimal("0.50"),
                decision.getRecoveryScore()
        );
    }

    @Test
    void shouldManuallyReviewWhenErrorCodeIsMissing() {

        RecoveryCase recoveryCase = createCase(
                null,
                "0.70"
        );

        RecoveryDecision decision =
                engine.determineStrategy(recoveryCase);

        assertEquals(
                RecoveryStrategy.MANUAL_REVIEW,
                decision.getStrategy()
        );

        assertEquals(
                RecoveryPriority.MEDIUM_HIGH,
                decision.getPriority()
        );
    }

    @Test
    void shouldManuallyReviewWhenScoreIsNull() {

        RecoveryCase recoveryCase = createCase(
                "INSUFFICIENT_FUNDS",
                null
        );

        RecoveryDecision decision =
                engine.determineStrategy(recoveryCase);

        assertEquals(
                RecoveryStrategy.MANUAL_REVIEW,
                decision.getStrategy()
        );

        assertEquals(
                RecoveryPriority.LOW,
                decision.getPriority()
        );

        assertNull(
                decision.getRecoveryScore()
        );
    }

    @Test
    void shouldRejectNullRecoveryCase() {

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.determineStrategy(null)
        );
    }

    @Test
    void shouldManuallyReviewWhenPaymentAttemptIsMissing() {

        RecoveryCase recoveryCase =
                RecoveryCase.builder()
                        .failedPayment(null)
                        .recoveryScore(
                                new BigDecimal("0.70")
                        )
                        .build();

        RecoveryDecision decision =
                engine.determineStrategy(recoveryCase);

        assertEquals(
                RecoveryStrategy.MANUAL_REVIEW,
                decision.getStrategy()
        );

        assertEquals(
                RecoveryPriority.LOW,
                decision.getPriority()
        );

        assertEquals(
                BigDecimal.ZERO,
                decision.getRecoveryScore()
        );

        assertTrue(
                decision.getReason()
                        .contains(
                                "Failed payment information is missing"
                        )
        );
    }

    private RecoveryCase createCase(
            String errorCode,
            String recoveryScore
    ) {

        PaymentAttempt paymentAttempt =
                PaymentAttempt.builder()
                        .externalPaymentId("pay_test_001")
                        .gatewayErrorCode(errorCode)
                        .build();

        return RecoveryCase.builder()
                .failedPayment(paymentAttempt)
                .recoveryScore(
                        recoveryScore == null
                                ? null
                                : new BigDecimal(recoveryScore)
                )
                .build();
    }
}

