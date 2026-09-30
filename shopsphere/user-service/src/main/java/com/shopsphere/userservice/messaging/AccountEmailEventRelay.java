package com.shopsphere.userservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * US41 — services publish {@link Outgoing} as a Spring event; it reaches Kafka only after the
 * surrounding DB transaction commits (or immediately when there is none).
 * Payloads contain single-use links: log ids only, never the payload.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountEmailEventRelay {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public record Outgoing(String topic, Long userId, Object payload) {
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void relay(Outgoing outgoing) {
        kafkaTemplate.send(outgoing.topic(), String.valueOf(outgoing.userId()), outgoing.payload())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for userId={}: {}", outgoing.topic(), outgoing.userId(), ex.getMessage());
                    } else {
                        log.info("Published {} for userId={}", outgoing.topic(), outgoing.userId());
                    }
                });
    }
}
