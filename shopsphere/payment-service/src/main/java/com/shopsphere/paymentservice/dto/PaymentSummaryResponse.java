package com.shopsphere.paymentservice.dto;

import com.shopsphere.paymentservice.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** US46 — payment totals for a period. successRate = SUCCESSFUL / (SUCCESSFUL + FAILED), null if none settled. */
public record PaymentSummaryResponse(
        Instant from,
        Instant to,
        long totalCount,
        List<StatusTotal> byStatus,
        Double successRate,
        long duplicateChargeOrders) {

    public record StatusTotal(PaymentStatus status, long count, BigDecimal amount) {}
}
