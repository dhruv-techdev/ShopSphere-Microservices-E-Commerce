package com.shopsphere.orderservice.service;

import com.shopsphere.common.events.OrderCancelledEvent;
import com.shopsphere.common.events.OrderItemSnapshot;
import com.shopsphere.orderservice.entity.CancellationReason;
import com.shopsphere.orderservice.entity.Order;

import java.time.Instant;
import java.util.UUID;

/** Builds order.cancelled payloads. Must be called inside the transaction (items are lazy). */
final class OrderCancelledEvents {

    private OrderCancelledEvents() {}

    static OrderCancelledEvent from(Order order, CancellationReason reason, Instant at) {
        return OrderCancelledEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(OrderCancelledEvent.TYPE)
                .occurredAt(at)
                .orderId(order.getId())
                .userId(order.getUserId())
                .reason(reason.name())
                .reasonDescription(reason.getDescription())
                .cancelledAt(at)
                .totalAmount(order.getTotalAmount())
                .items(order.getItems().stream()
                        .map(i -> OrderItemSnapshot.builder()
                                .productId(i.getProductId())
                                .quantity(i.getQuantity())
                                .unitPrice(i.getUnitPrice())
                                .build())
                        .toList())
                .build();
    }
}
