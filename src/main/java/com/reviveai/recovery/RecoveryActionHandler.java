package com.reviveai.recovery;

import com.reviveai.entity.RecoveryCase;

/**
 * Contract for executing a recovery strategy.
 *
 * Each recovery strategy must have its own handler implementation.
 */
public interface RecoveryActionHandler {

    /**
     * Returns the recovery strategy handled by this implementation.
     */
    RecoveryStrategy getStrategy();

    /**
     * Executes the recovery action and returns its outcome.
     */
    RecoveryOutcome handle(
            RecoveryCase recoveryCase,
            RecoveryDecision decision
    );
}