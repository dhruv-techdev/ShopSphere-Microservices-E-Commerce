package com.shopsphere.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {

    /** US33 — required; snapshotted onto the order and into the order.created event. */
    @NotNull(message = "Shipping address is required")
    @Valid
    private AddressDto shippingAddress;

    /** Optional shipping note or customer instruction. Reserved for future use. */
    @Size(max = 500, message = "Notes must be at most 500 characters")
    private String notes;
}
