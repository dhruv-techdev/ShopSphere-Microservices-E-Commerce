package com.shopsphere.inventoryservice.messaging;

import com.shopsphere.common.events.LowStockEvent;
import com.shopsphere.common.events.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishLowStock(LowStockEvent event) {
        String key = String.valueOf(event.getProductId());
        kafkaTemplate.send(Topics.LOW_STOCK, key, event)
                .whenComplete((res, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish inventory.low-stock for productId={}: {}",
                                event.getProductId(), ex.getMessage(), ex);
                    } else {
                        log.info("Published inventory.low-stock eventId={} productId={} sellable={}",
                                event.getEventId(), event.getProductId(), event.getSellableQuantity());
                    }
                });
    }
}
