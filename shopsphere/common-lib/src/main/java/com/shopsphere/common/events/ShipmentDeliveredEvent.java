package com.shopsphere.common.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Published by the .NET shipping-service (US36) when a shipment is marked DELIVERED.
 * eventId is deterministic per shipment so re-publishes can be de-duplicated.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShipmentDeliveredEvent extends BaseEvent {

    public static final String TYPE = "shipment.delivered.v1";

    private Long shipmentId;
    private Long orderId;
    private Long userId;
    private String carrier;
    private String trackingNumber;
    private Instant deliveredAt;
}
