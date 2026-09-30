package com.shopsphere.orderservice.messaging;

import com.shopsphere.common.events.ReservationExpiredEvent;
import com.shopsphere.common.events.Topics;
import com.shopsphere.orderservice.service.OrderCancellationService;
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
public class ReservationExpiredConsumer {

    private final OrderCancellationService orderCancellationService;

    @KafkaListener(
            topics = Topics.RESERVATION_EXPIRED,
            groupId = "order-service",
            containerFactory = "reservationExpiredListenerContainerFactory"
    )
    public void onReservationExpired(
            @Payload ReservationExpiredEvent event,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Order received inventory.reservation-expired eventId={} orderId={} offset={}",
                event.getEventId(), event.getOrderId(), offset);
        try {
            orderCancellationService.onReservationExpired(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to handle inventory.reservation-expired eventId={}: {}",
                    event.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }
}
