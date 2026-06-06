package com.shopsphere.paymentservice.messaging;

import com.shopsphere.common.events.PaymentFailedEvent;
import com.shopsphere.common.events.PaymentSuccessfulEvent;
import com.shopsphere.common.events.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishSuccessful(PaymentSuccessfulEvent event) {
        String key = String.valueOf(event.getOrderId());
        kafkaTemplate.send(Topics.PAYMENT_SUCCESSFUL, key, event)
                .whenComplete((res, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish payment.successful for orderId={}: {}",
                                event.getOrderId(), ex.getMessage(), ex);
                    } else {
                        log.info("Published payment.successful eventId={} orderId={}",
                                event.getEventId(), event.getOrderId());
                    }
                });
    }

    public void publishFailed(PaymentFailedEvent event) {
        String key = String.valueOf(event.getOrderId());
        kafkaTemplate.send(Topics.PAYMENT_FAILED, key, event)
                .whenComplete((res, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish payment.failed for orderId={}: {}",
                                event.getOrderId(), ex.getMessage(), ex);
                    } else {
                        log.info("Published payment.failed eventId={} orderId={} reason={}",
                                event.getEventId(), event.getOrderId(), event.getReason());
                    }
                });
    }
}
