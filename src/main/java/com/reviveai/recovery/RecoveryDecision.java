package com.reviveai.recovery;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class RecoveryDecision {

    private RecoveryStrategy strategy;

    private RecoveryPriority priority;

    private BigDecimal recoveryScore;

    private String reason;
}