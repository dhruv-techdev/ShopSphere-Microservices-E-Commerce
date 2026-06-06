package com.shopsphere.common.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LowStockEvent extends BaseEvent {

    public static final String TYPE = "inventory.low-stock.v1";

    private Long productId;
    private Integer availableQuantity;
    private Integer reservedQuantity;
    private Integer sellableQuantity;
    private Integer threshold;
}
