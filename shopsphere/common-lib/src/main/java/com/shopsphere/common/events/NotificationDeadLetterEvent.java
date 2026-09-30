package com.shopsphere.common.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * US40 — a notification that could not be delivered, parked on notification.dlq.
 * Keyed by notificationId. Carries enough context to investigate and replay.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationDeadLetterEvent extends BaseEvent {

    public static final String TYPE = "notification.dead-letter.v1";

    private Long notificationId;
    private String notificationType;
    private Long userId;
    private Long orderId;
    private String sourceEventId;
    private String channel;
    private String recipient;
    private Integer attempts;

    /** PERMANENT_FAILURE | RETRIES_EXHAUSTED */
    private String reason;
    private String lastError;
    private Instant failedAt;
}
