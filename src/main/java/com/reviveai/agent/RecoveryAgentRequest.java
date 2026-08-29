package com.reviveai.agent;

import com.reviveai.entity.RecoveryCase;
import com.reviveai.recovery.RecoveryPriority;
import com.reviveai.recovery.RecoveryStrategy;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryAgentRequest {

    /**
     * Recovery case being evaluated.
     */
    private UUID recoveryCaseId;

    /**
     * ML predicted recovery probability.
     *
     * Expected range:
     * 0.00 - 1.00
     */
    private BigDecimal recoveryScore;

    /**
     * Payment amount at risk.
     */
    private BigDecimal paymentAmount;

    /**
     * Recovery potential calculated by the
     * deterministic recovery engine.
     */
    private RecoveryCase.RecoveryPotential recoveryPotential;

    /**
     * Payment gateway error code.
     */
    private String paymentErrorCode;

    /**
     * Number of previous payment retry attempts.
     */
    private Integer retryCount;

    /**
     * Historical payment failure rate.
     */
    private BigDecimal paymentFailureRate;

    /**
     * Strategy selected by the rule-based engine.
     */
    private RecoveryStrategy ruleBasedStrategy;

    /**
     * Priority selected by the rule-based engine.
     */
    private RecoveryPriority ruleBasedPriority;
}