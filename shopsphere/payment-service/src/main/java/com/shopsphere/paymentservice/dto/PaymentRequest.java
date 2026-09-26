package com.shopsphere.paymentservice.dto;

import com.shopsphere.common.events.OrderCreatedEvent;
import com.shopsphere.common.events.OrderItemSnapshot;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {

    @NotNull(message = "orderId is required")
    @Positive(message = "orderId must be positive")
    private Long orderId;

    @NotNull(message = "userId is required")
    @Positive(message = "userId must be positive")
    private Long userId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "amount must have at most 2 decimal places")
    private BigDecimal amount;

    /**
     * Optional: force a specific outcome regardless of the global success rate.
     * Useful for tests and integration runs. Production gateways have a similar concept
     * (test mode "always succeed" / "always fail" credentials).
     */
    private SimulationMode mode;

    private List<OrderItemSnapshot> items;

    /** US35 — optional; forwarded onto payment.successful for shipping-service. */
    private OrderCreatedEvent.ShippingAddress shippingAddress;

    public enum SimulationMode {
        ALWAYS_SUCCEED,
        ALWAYS_FAIL,
        RANDOM
    }
}
