package com.reviveai.dto;

import com.reviveai.entity.Subscription;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class SubscriptionResponse {

    private UUID id;

    private UUID customerId;

    private String externalSubscriptionId;

    private Subscription.SubscriptionStatus status;

    private BigDecimal amount;

    private String currency;

    private OffsetDateTime nextBillingAt;

    private BigDecimal riskScore;

    private OffsetDateTime createdAt;
}
