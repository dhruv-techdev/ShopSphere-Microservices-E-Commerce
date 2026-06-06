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
public class PaymentFailedEvent extends BaseEvent {

    public static final String TYPE = "payment.failed.v1";

    private Long orderId;
    private Long userId;
    private String paymentReference;
    private BigDecimal amount;
    private String reason;

    /** Items so inventory can release reservations. */
    private List<OrderItemSnapshot> items;
}
