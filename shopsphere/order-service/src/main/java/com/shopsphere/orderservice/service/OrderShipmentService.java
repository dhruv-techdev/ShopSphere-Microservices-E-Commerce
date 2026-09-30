package com.shopsphere.orderservice.service;

import com.shopsphere.common.events.ShipmentDeliveredEvent;
import com.shopsphere.common.events.ShipmentDispatchedEvent;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.entity.ProcessedEvent;
import com.shopsphere.orderservice.repository.OrderRepository;
import com.shopsphere.orderservice.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * US36 — applies shipping-service events to orders.
 * Idempotent per eventId; transitions follow {@link OrderStatus#canTransitionTo}.
 * Shipment details (carrier, tracking, timestamps) are filled in even when the status
 * can't move (e.g. a late dispatched event after delivery), but never overwritten.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderShipmentService {

    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void onShipmentDispatched(ShipmentDispatchedEvent event) {
        if (!shouldProcess(event.getEventId(), "shipment.dispatched")) {
            return;
        }

        orderRepository.findById(event.getOrderId()).ifPresentOrElse(order -> {
            fillShipmentDetails(order, event.getShipmentId(), event.getCarrier(), event.getTrackingNumber());
            if (order.getShippedAt() == null) {
                order.setShippedAt(event.getShippedAt());
            }
            transition(order, OrderStatus.SHIPPED, event.getEventId());
            orderRepository.save(order);
        }, () -> log.warn("shipment.dispatched eventId={} references unknown order {}",
                event.getEventId(), event.getOrderId()));

        markProcessed(event.getEventId(), event.getEventType(), ShipmentDispatchedEvent.TYPE);
    }

    @Transactional
    public void onShipmentDelivered(ShipmentDeliveredEvent event) {
        if (!shouldProcess(event.getEventId(), "shipment.delivered")) {
            return;
        }

        orderRepository.findById(event.getOrderId()).ifPresentOrElse(order -> {
            fillShipmentDetails(order, event.getShipmentId(), event.getCarrier(), event.getTrackingNumber());
            if (order.getDeliveredAt() == null) {
                order.setDeliveredAt(event.getDeliveredAt());
            }
            transition(order, OrderStatus.DELIVERED, event.getEventId());
            orderRepository.save(order);
        }, () -> log.warn("shipment.delivered eventId={} references unknown order {}",
                event.getEventId(), event.getOrderId()));

        markProcessed(event.getEventId(), event.getEventType(), ShipmentDeliveredEvent.TYPE);
    }

    /* ---------------------------------------------------------------- */

    private boolean shouldProcess(String eventId, String topic) {
        if (eventId == null || eventId.isBlank()) {
            log.warn("Ignoring {} without eventId", topic);
            return false;
        }
        if (processedEventRepository.existsById(eventId)) {
            log.info("Skipping {} eventId={} — already processed", topic, eventId);
            return false;
        }
        return true;
    }

    private void markProcessed(String eventId, String eventType, String fallbackType) {
        processedEventRepository.save(ProcessedEvent.of(eventId, eventType != null ? eventType : fallbackType));
    }

    private static void fillShipmentDetails(Order order, Long shipmentId, String carrier, String trackingNumber) {
        if (order.getShipmentId() == null) {
            order.setShipmentId(shipmentId);
        }
        if (order.getCarrier() == null) {
            order.setCarrier(carrier);
        }
        if (order.getTrackingNumber() == null) {
            order.setTrackingNumber(trackingNumber);
        }
    }

    private void transition(Order order, OrderStatus target, String eventId) {
        OrderStatus current = order.getStatus();
        if (current == target) {
            return;
        }
        if (!current.canTransitionTo(target)) {
            log.warn("Order {} is {} — ignoring transition to {} (eventId={})",
                    order.getId(), current, target, eventId);
            return;
        }
        order.setStatus(target);
        log.info("Order {} {} -> {} (eventId={}, at={})", order.getId(), current, target, eventId, Instant.now());
    }
}
