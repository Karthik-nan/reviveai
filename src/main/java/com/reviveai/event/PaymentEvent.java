package com.reviveai.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {

    private UUID eventId;

    private String eventType;

    private String externalPaymentId;

    private UUID subscriptionId;

    private BigDecimal amount;

    private String currency;

    private String failureCode;

    private String failureMessage;

    private OffsetDateTime occurredAt;
}
