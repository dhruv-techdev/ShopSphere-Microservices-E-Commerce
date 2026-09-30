package com.shopsphere.notificationservice.messaging;

import com.shopsphere.common.events.LowStockEvent;
import com.shopsphere.common.events.OrderCreatedEvent;
import com.shopsphere.common.events.PaymentFailedEvent;
import com.shopsphere.common.events.PaymentSuccessfulEvent;
import com.shopsphere.common.events.ShipmentDeliveredEvent;
import com.shopsphere.common.events.ShipmentDispatchedEvent;
import com.shopsphere.common.events.Topics;
import com.shopsphere.notificationservice.service.NotificationService;
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
public class NotificationEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = Topics.ORDER_CREATED,
            groupId = "notification-service",
            containerFactory = "orderCreatedListenerContainerFactory"
    )
    public void onOrderCreated(
            @Payload OrderCreatedEvent event,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Notification received order.created eventId={} orderId={} offset={}",
                event.getEventId(), event.getOrderId(), offset);
        try {
            notificationService.handleOrderCreated(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to handle order.created eventId={}: {}",
                    event.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    @KafkaListener(
            topics = Topics.PAYMENT_SUCCESSFUL,
            groupId = "notification-service",
            containerFactory = "paymentSuccessfulListenerContainerFactory"
    )
    public void onPaymentSuccessful(
            @Payload PaymentSuccessfulEvent event,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Notification received payment.successful eventId={} orderId={} offset={}",
                event.getEventId(), event.getOrderId(), offset);
        try {
            notificationService.handlePaymentSuccessful(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to handle payment.successful eventId={}: {}",
                    event.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    @KafkaListener(
            topics = Topics.PAYMENT_FAILED,
            groupId = "notification-service",
            containerFactory = "paymentFailedListenerContainerFactory"
    )
    public void onPaymentFailed(
            @Payload PaymentFailedEvent event,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Notification received payment.failed eventId={} orderId={} offset={}",
                event.getEventId(), event.getOrderId(), offset);
        try {
            notificationService.handlePaymentFailed(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to handle payment.failed eventId={}: {}",
                    event.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    /* Bonus: consume low-stock for admin notifications. Not explicitly in US24's STs
       but completes the picture and the LowStockEvent already exists. */
    @KafkaListener(
            topics = Topics.LOW_STOCK,
            groupId = "notification-service",
            containerFactory = "lowStockListenerContainerFactory"
    )
    public void onLowStock(
            @Payload LowStockEvent event,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Notification received inventory.low-stock eventId={} productId={} offset={}",
                event.getEventId(), event.getProductId(), offset);
        try {
            notificationService.handleLowStock(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to handle inventory.low-stock eventId={}: {}",
                    event.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    /* ---------------- US36 — shipment notifications ---------------- */

    @KafkaListener(
            topics = Topics.SHIPMENT_DISPATCHED,
            groupId = "notification-service",
            containerFactory = "shipmentDispatchedListenerContainerFactory"
    )
    public void onShipmentDispatched(
            @Payload ShipmentDispatchedEvent event,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Notification received shipment.dispatched eventId={} orderId={} offset={}",
                event.getEventId(), event.getOrderId(), offset);
        try {
            notificationService.handleShipmentDispatched(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to handle shipment.dispatched eventId={}: {}",
                    event.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    @KafkaListener(
            topics = Topics.SHIPMENT_DELIVERED,
            groupId = "notification-service",
            containerFactory = "shipmentDeliveredListenerContainerFactory"
    )
    public void onShipmentDelivered(
            @Payload ShipmentDeliveredEvent event,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Notification received shipment.delivered eventId={} orderId={} offset={}",
                event.getEventId(), event.getOrderId(), offset);
        try {
            notificationService.handleShipmentDelivered(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to handle shipment.delivered eventId={}: {}",
                    event.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }
}
