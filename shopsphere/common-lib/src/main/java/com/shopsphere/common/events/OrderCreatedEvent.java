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
public class OrderCreatedEvent extends BaseEvent {

    public static final String TYPE = "order.created.v1";

    private Long orderId;
    private Long userId;
    private BigDecimal totalAmount;
    private Integer itemCount;
    private List<Item> items;

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
}
