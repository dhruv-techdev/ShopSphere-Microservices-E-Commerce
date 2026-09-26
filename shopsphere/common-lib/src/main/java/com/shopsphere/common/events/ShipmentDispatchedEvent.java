package com.shopsphere.common.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Published by the .NET shipping-service (US35) once a shipment has been created and
 * handed to the carrier. Declared here so Java services can consume it with the same
 * JsonDeserializer setup as every other event.
 *
 * <p>eventId is deterministic per shipment, so a re-publish after a failure carries the
 * same id and consumers can de-duplicate.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShipmentDispatchedEvent extends BaseEvent {

    public static final String TYPE = "shipment.dispatched.v1";

    private Long shipmentId;
    private Long orderId;
    private Long userId;
    private String carrier;
    private String trackingNumber;
    private Instant shippedAt;
    private OrderCreatedEvent.ShippingAddress shippingAddress;
}
