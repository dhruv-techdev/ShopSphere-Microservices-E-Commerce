package com.shopsphere.orderservice.dto;

import com.shopsphere.orderservice.entity.CancellationReason;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** US42 — list row for the admin order table (includes the owner, unlike the customer summary). */
public record AdminOrderSummaryResponse(
        Long id,
        Long userId,
        OrderStatus status,
        BigDecimal totalAmount,
        Integer itemCount,
        String trackingNumber,
        CancellationReason cancellationReason,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminOrderSummaryResponse from(Order o) {
        return new AdminOrderSummaryResponse(o.getId(), o.getUserId(), o.getStatus(), o.getTotalAmount(),
                o.getItemCount(), o.getTrackingNumber(), o.getCancellationReason(), o.getCreatedAt(), o.getUpdatedAt());
    }
}
