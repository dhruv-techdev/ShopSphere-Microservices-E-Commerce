package com.shopsphere.cartservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
// getTotal()/getItemCount() are written to Redis as "total"/"itemCount" but have no setters.
@JsonIgnoreProperties(ignoreUnknown = true)
public class Cart implements Serializable {

    /** Owning customer's user id (acts as the Redis key suffix). */
    private Long userId;

    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    private Instant createdAt;
    private Instant updatedAt;

    public BigDecimal getTotal() {
        return items == null ? BigDecimal.ZERO :
                items.stream()
                        .map(CartItem::getLineTotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int getItemCount() {
        return items == null ? 0 :
                items.stream().mapToInt(CartItem::getQuantity).sum();
    }
}
