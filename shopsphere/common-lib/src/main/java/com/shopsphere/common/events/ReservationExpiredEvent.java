package com.shopsphere.common.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;

/**
 * US37 — published by inventory-service when an order's stock holds time out before
 * payment settled. One event per order (keyed by orderId), listing every released line.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReservationExpiredEvent extends BaseEvent {

    public static final String TYPE = "inventory.reservation-expired.v1";

    private Long orderId;
    private Instant expiredAt;

    /** productId + quantity released (unitPrice not set). */
    private List<OrderItemSnapshot> items;
}
