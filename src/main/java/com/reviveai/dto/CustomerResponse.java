package com.reviveai.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class CustomerResponse {

    private UUID id;

    private String externalCustomerId;

    private String email;

    private OffsetDateTime createdAt;

    private int subscriptionCount;

    private int activeSubscriptions;

    private int pastDueSubscriptions;

    private int cancelledSubscriptions;

    private int recoveryCaseCount;

    private BigDecimal revenueAtRisk;

    private BigDecimal revenueRecovered;
}
