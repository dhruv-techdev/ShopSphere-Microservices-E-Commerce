package com.shopsphere.inventoryservice.messaging;

import com.shopsphere.common.events.OrderCreatedEvent;
import com.shopsphere.common.events.PaymentFailedEvent;
import com.shopsphere.common.events.PaymentSuccessfulEvent;
import com.shopsphere.common.events.Topics;
import com.shopsphere.inventoryservice.service.InventoryService;
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
public class InventoryEventConsumer {

    private final InventoryService inventoryService;

    @KafkaListener(
            topics = Topics.ORDER_CREATED,
            groupId = "inventory-service",
            containerFactory = "orderCreatedListenerContainerFactory"
    )
    public void onOrderCreated(
            @Payload OrderCreatedEvent event,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Inventory received order.created eventId={} orderId={} offset={}",
                event.getEventId(), event.getOrderId(), offset);
        try {
            inventoryService.reserveForOrder(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to reserve for order.created eventId={}: {}",
                    event.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    @KafkaListener(
            topics = Topics.PAYMENT_SUCCESSFUL,
            groupId = "inventory-service",
            containerFactory = "paymentSuccessfulListenerContainerFactory"
    )
    public void onPaymentSuccessful(
            @Payload PaymentSuccessfulEvent event,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Inventory received payment.successful eventId={} orderId={} offset={}",
                event.getEventId(), event.getOrderId(), offset);
        try {
            inventoryService.commitReservation(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to commit reservation for payment.successful eventId={}: {}",
                    event.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    @KafkaListener(
            topics = Topics.PAYMENT_FAILED,
            groupId = "inventory-service",
            containerFactory = "paymentFailedListenerContainerFactory"
    )
    public void onPaymentFailed(
            @Payload PaymentFailedEvent event,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Inventory received payment.failed eventId={} orderId={} offset={}",
                event.getEventId(), event.getOrderId(), offset);
        try {
            inventoryService.releaseReservation(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to release reservation for payment.failed eventId={}: {}",
                    event.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }
}
