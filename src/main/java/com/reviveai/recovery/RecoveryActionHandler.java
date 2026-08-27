package com.reviveai.recovery;

import com.reviveai.entity.RecoveryCase;

public interface RecoveryActionHandler {

    RecoveryStrategy getStrategy();

    void handle(
            RecoveryCase recoveryCase,
            RecoveryDecision decision
    );
}