package com.shopsphere.notificationservice.messaging;

import com.shopsphere.common.events.OrderCancelledEvent;
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

/** US38 — tells the customer their order was cancelled. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCancelledConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = Topics.ORDER_CANCELLED,
            groupId = "notification-service",
            containerFactory = "orderCancelledListenerContainerFactory"
    )
    public void onOrderCancelled(
            @Payload OrderCancelledEvent event,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Notification received order.cancelled eventId={} orderId={} reason={} offset={}",
                event.getEventId(), event.getOrderId(), event.getReason(), offset);
        try {
            notificationService.handleOrderCancelled(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to handle order.cancelled eventId={}: {}",
                    event.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }
}
