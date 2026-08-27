package com.reviveai.recovery;

import com.reviveai.entity.RecoveryCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class RecoveryDecisionGuard {

    private static final BigDecimal MIN_AUTOMATION_SCORE =
            new BigDecimal("0.40");

    public GuardResult validate(
            RecoveryCase recoveryCase,
            RecoveryDecision decision
    ) {

        if (recoveryCase == null) {
            throw new IllegalArgumentException(
                    "Recovery case cannot be null"
            );
        }

        if (decision == null) {
            throw new IllegalArgumentException(
                    "Recovery decision cannot be null"
            );
        }

        RecoveryStrategy strategy =
                decision.getStrategy();

        BigDecimal score =
                decision.getRecoveryScore();

        /*
         * No strategy means the system cannot safely
         * determine what action should be taken.
         */
        if (strategy == null) {

            return reject(
                    "Recovery decision does not contain a strategy"
            );
        }

        /*
         * Manual review is always an allowed terminal
         * decision because it does not perform an
         * automated financial action.
         */
        if (strategy == RecoveryStrategy.MANUAL_REVIEW) {

            return approve(
                    "Manual review decision is allowed"
            );
        }

        /*
         * Automated recovery requires a recovery score.
         */
        if (score == null) {

            return reject(
                    "Automated recovery requires a recovery score"
            );
        }

        /*
         * Prevent invalid score values.
         */
        if (score.compareTo(BigDecimal.ZERO) < 0 ||
                score.compareTo(BigDecimal.ONE) > 0) {

            return reject(
                    "Recovery score must be between 0.00 and 1.00"
            );
        }

        /*
         * Do not allow automated recovery when confidence
         * is below the minimum threshold.
         */
        if (score.compareTo(MIN_AUTOMATION_SCORE) < 0) {

            return reject(
                    "Recovery score is below the minimum automation threshold"
            );
        }

        log.info(
                "Recovery decision guard approved. " +
                        "recoveryCaseId={}, strategy={}, score={}",
                recoveryCase.getId(),
                strategy,
                score
        );

        return approve(
                "Recovery decision passed all safety checks"
        );
    }

    private GuardResult approve(String reason) {

        return GuardResult.builder()
                .allowed(true)
                .reason(reason)
                .build();
    }

    private GuardResult reject(String reason) {

        return GuardResult.builder()
                .allowed(false)
                .reason(reason)
                .build();
    }

    @lombok.Getter
    @lombok.Builder
    public static class GuardResult {

        private boolean allowed;

        private String reason;
    }
}