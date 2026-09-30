package com.shopsphere.common.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * US38 — published by order-service after an order is cancelled (committed).
 * Carries enough for notification now, and for refunds/stock compensation later.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderCancelledEvent extends BaseEvent {

    public static final String TYPE = "order.cancelled.v1";

    private Long orderId;
    private Long userId;

    /** Machine-readable reason code, e.g. RESERVATION_EXPIRED. */
    private String reason;

    /** Customer-facing explanation. */
    private String reasonDescription;

    private Instant cancelledAt;
    private BigDecimal totalAmount;
    private List<OrderItemSnapshot> items;
}
