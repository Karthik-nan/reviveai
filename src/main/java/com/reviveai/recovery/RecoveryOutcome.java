package com.reviveai.recovery;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class RecoveryOutcome {

    private final OutcomeStatus status;

    private final BigDecimal amountRecovered;

    private final String reason;

    public enum OutcomeStatus {

        RECOVERED,

        FAILED
    }
}