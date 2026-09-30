package com.shopsphere.notificationservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_user", columnList = "user_id"),
        @Index(name = "idx_notifications_type", columnList = "type"),
        @Index(name = "idx_notifications_created_at", columnList = "created_at"),
        @Index(name = "idx_notifications_delivery_status", columnList = "delivery_status"),
        @Index(name = "idx_notifications_retry_due", columnList = "delivery_status, next_attempt_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    /** Recipient user. Null for system-level alerts like LOW_STOCK_ALERT (sent to the admin address). */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    /** Plain-text body (also used as the text/plain part of the email). */
    @Column(name = "body", nullable = false, length = 2000)
    private String body;

    /** Linked order id when relevant. */
    @Column(name = "order_id")
    private Long orderId;

    /** Source event ID for idempotency. Same inbox pattern as inventory-service. */
    @Column(name = "source_event_id", nullable = false, length = 36)
    private String sourceEventId;

    /** US39/US40 — set from the actual send result. */
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    @Builder.Default
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    /** log | smtp | sendgrid */
    @Column(name = "channel", length = 20)
    private String channel;

    @Column(name = "recipient", length = 254)
    private String recipient;

    @Column(name = "provider_message_id", length = 200)
    private String providerMessageId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "sent_at")
    private Instant sentAt;

    /* ---------------- US40 — retry bookkeeping ---------------- */

    /** Send attempts made so far. */
    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    /** When the retry poller should try again (RETRYING only). Also used as a claim lease. */
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "dead_lettered_at")
    private Instant deadLetteredAt;

    /** JSON of the template model, so retries re-render exactly the same email. */
    @Column(name = "template_model", columnDefinition = "text")
    private String templateModel;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
