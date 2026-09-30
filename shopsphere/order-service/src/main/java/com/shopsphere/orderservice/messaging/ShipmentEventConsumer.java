package com.shopsphere.orderservice.messaging;

import com.shopsphere.common.events.ShipmentDeliveredEvent;
import com.shopsphere.common.events.ShipmentDispatchedEvent;
import com.shopsphere.common.events.Topics;
import com.shopsphere.orderservice.service.OrderShipmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentEventConsumer {

    private final OrderShipmentService orderShipmentService;

    @KafkaListener(
            topics = Topics.SHIPMENT_DISPATCHED,
            groupId = "order-service",
            containerFactory = "shipmentDispatchedListenerContainerFactory"
    )
    public void onShipmentDispatched(
            @Payload ShipmentDispatchedEvent event,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Order received shipment.dispatched eventId={} orderId={} tracking={} offset={}",
                event.getEventId(), event.getOrderId(), event.getTrackingNumber(), offset);
        try {
            orderShipmentService.onShipmentDispatched(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to handle shipment.dispatched eventId={}: {}",
                    event.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    @KafkaListener(
            topics = Topics.SHIPMENT_DELIVERED,
            groupId = "order-service",
            containerFactory = "shipmentDeliveredListenerContainerFactory"
    )
    public void onShipmentDelivered(
            @Payload ShipmentDeliveredEvent event,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Order received shipment.delivered eventId={} orderId={} offset={}",
                event.getEventId(), event.getOrderId(), offset);
        try {
            orderShipmentService.onShipmentDelivered(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to handle shipment.delivered eventId={}: {}",
                    event.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }
}
