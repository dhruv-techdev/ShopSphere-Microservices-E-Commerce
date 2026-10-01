package com.shopsphere.paymentservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** US46 — an order captured more than once (refund the extras). */
public record DuplicateChargeResponse(
        Long orderId,
        Long userId,
        long successfulPayments,
        BigDecimal totalCaptured,
        List<String> paymentReferences,
        Instant lastCapturedAt) {
}
