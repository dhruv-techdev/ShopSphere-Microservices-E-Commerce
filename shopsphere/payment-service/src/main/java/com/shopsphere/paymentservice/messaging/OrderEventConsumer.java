package com.shopsphere.paymentservice.messaging;

import com.shopsphere.common.events.OrderCreatedEvent;
import com.shopsphere.common.events.Topics;
import com.shopsphere.paymentservice.service.PaymentService;
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
public class OrderEventConsumer {

    private final PaymentService paymentService;

    /**
     * Consumes order.created events and triggers payment simulation.
     * Auto-commit is disabled in application.yml — we ack manually only after success,
     * so a crash mid-processing causes Kafka to redeliver. The payment service handles
     * idempotency by checking if a payment for this order+eventId already exists.
     */
    @KafkaListener(
            topics = Topics.ORDER_CREATED,
            groupId = "payment-service",
            containerFactory = "orderCreatedListenerContainerFactory"
    )
    public void handleOrderCreated(
            @Payload OrderCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Received order.created eventId={} orderId={} partition={} offset={}",
                event.getEventId(), event.getOrderId(), partition, offset);

        try {
            paymentService.processOrderCreated(event);
            ack.acknowledge();
            log.debug("Processed eventId={} successfully; offset {} committed",
                    event.getEventId(), offset);
        } catch (Exception ex) {
            // Don't ack — Kafka will redeliver on next poll
            log.error("Failed to process order.created eventId={} orderId={}: {}",
                    event.getEventId(), event.getOrderId(), ex.getMessage(), ex);
            throw ex;
        }
    }
}
