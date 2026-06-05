package com.shopsphere.common.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Common envelope for every Kafka event. Provides:
 * - eventId: globally unique, used by consumers for idempotency.
 * - eventType: lets a single topic carry multiple subtypes if ever needed.
 * - occurredAt: when the source domain action happened (not when serialized).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class BaseEvent {
    private String eventId;
    private String eventType;
    private Instant occurredAt;

    protected BaseEvent(String eventType) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.occurredAt = Instant.now();
    }
}
