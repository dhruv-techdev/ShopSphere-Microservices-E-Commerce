package com.shopsphere.orderservice.messaging;

import com.shopsphere.common.events.OrderCancelledEvent;
import com.shopsphere.common.events.OrderCreatedEvent;
import com.shopsphere.common.events.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderCreated(OrderCreatedEvent event) {
        kafkaTemplate.send(Topics.ORDER_CREATED, String.valueOf(event.getOrderId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderCreatedEvent for order {}: {}",
                                event.getOrderId(), ex.getMessage());
                    } else {
                        log.info("Published OrderCreatedEvent for order {} to topic {} partition {}",
                                event.getOrderId(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition());
                    }
                });
    }

    /** US38 */
    public void publishOrderCancelled(OrderCancelledEvent event) {
        kafkaTemplate.send(Topics.ORDER_CANCELLED, String.valueOf(event.getOrderId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderCancelledEvent for order {}: {}",
                                event.getOrderId(), ex.getMessage());
                    } else {
                        log.info("Published OrderCancelledEvent for order {} reason={} to partition {}",
                                event.getOrderId(), event.getReason(),
                                result.getRecordMetadata().partition());
                    }
                });
    }
}
