package com.reviveai.dto;

import com.reviveai.entity.RecoveryAction;
import com.reviveai.recovery.RecoveryPriority;
import com.reviveai.recovery.RecoveryStrategy;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class RecoveryActionResponse {

    private Long id;

    private UUID recoveryCaseId;

    private RecoveryStrategy strategy;

    private RecoveryPriority priority;

    private RecoveryAction.ActionStatus status;

    private String reason;

    private BigDecimal recoveryScore;

    private LocalDateTime createdAt;

    private LocalDateTime executedAt;
}