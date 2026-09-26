package com.shopsphere.common.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentSuccessfulEvent extends BaseEvent {

    public static final String TYPE = "payment.successful.v1";

    private Long orderId;
    private Long userId;
    private String paymentReference;
    private BigDecimal amount;

    /** Items so inventory can deduct without a callback. */
    private List<OrderItemSnapshot> items;

    /**
     * US35 — carried forward from order.created so shipping-service can create the
     * shipment without calling order-service. Additive: type stays v1.
     */
    private OrderCreatedEvent.ShippingAddress shippingAddress;
}
