package com.reviveai.dto;

import com.reviveai.entity.RecoveryCase;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class RecoveryCaseResponse {

    private UUID id;

    private UUID subscriptionId;

    private UUID failedPaymentId;

    private RecoveryCase.RecoveryStatus status;

    private RecoveryCase.RecoveryPotential recoveryPotential;

    private BigDecimal recoveryScore;

    private BigDecimal amountAtRisk;

    private BigDecimal amountRecovered;

    private OffsetDateTime createdAt;

    private OffsetDateTime resolvedAt;
}