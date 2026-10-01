package com.shopsphere.orderservice.messaging;

import com.shopsphere.common.events.PaymentFailedEvent;
import com.shopsphere.common.events.PaymentSuccessfulEvent;
import com.shopsphere.common.events.Topics;
import com.shopsphere.orderservice.service.OrderPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** US45 — order-service follows payment outcomes (closes the long-standing PAID gap). */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final OrderPaymentService orderPaymentService;

    @KafkaListener(
            topics = Topics.PAYMENT_SUCCESSFUL,
            groupId = "order-service",
            containerFactory = "paymentSuccessfulListenerContainerFactory"
    )
    public void onPaymentSuccessful(
            @Payload PaymentSuccessfulEvent event,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Order received payment.successful eventId={} orderId={} offset={}",
                event.getEventId(), event.getOrderId(), offset);
        try {
            orderPaymentService.onPaymentSuccessful(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to handle payment.successful eventId={}: {}",
                    event.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    @KafkaListener(
            topics = Topics.PAYMENT_FAILED,
            groupId = "order-service",
            containerFactory = "paymentFailedListenerContainerFactory"
    )
    public void onPaymentFailed(
            @Payload PaymentFailedEvent event,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Order received payment.failed eventId={} orderId={} offset={}",
                event.getEventId(), event.getOrderId(), offset);
        try {
            orderPaymentService.onPaymentFailed(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to handle payment.failed eventId={}: {}",
                    event.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }
}
