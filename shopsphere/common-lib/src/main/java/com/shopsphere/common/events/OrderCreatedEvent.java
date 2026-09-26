package com.shopsphere.common.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.List;

/**
 * Published by order-service after an order is committed.
 *
 * <p>US33 added {@code shippingAddress}. The change is additive, so the type stays at v1:
 * consumers built against the old class ignore the unknown field.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderCreatedEvent extends BaseEvent {

    public static final String TYPE = "order.created.v1";

    private Long orderId;
    private Long userId;
    private BigDecimal totalAmount;
    private Integer itemCount;
    private List<Item> items;
    private ShippingAddress shippingAddress;

    @Data
    @NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item {
        private Long productId;
        private String productName;
        private BigDecimal unitPrice;
        private Integer quantity;
    }

    /** Snapshot of the shipping address at the time the order was placed. */
    @Data
    @NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ShippingAddress {
        private String recipientName;
        private String phone;
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String postalCode;
        /** ISO 3166-1 alpha-2, upper case (e.g. "CA", "US"). */
        private String country;
    }
}
