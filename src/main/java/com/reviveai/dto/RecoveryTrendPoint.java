package com.reviveai.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class RecoveryTrendPoint {

    private LocalDate date;

    private BigDecimal amountRecovered;
}