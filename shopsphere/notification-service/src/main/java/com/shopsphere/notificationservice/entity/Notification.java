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
        @Index(name = "idx_notifications_created_at", columnList = "created_at")
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

    /** Recipient. Null for system-level alerts like LOW_STOCK_ALERT. */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "body", nullable = false, length = 2000)
    private String body;

    /** Linked order id when relevant. */
    @Column(name = "order_id")
    private Long orderId;

    /**
     * Source event ID for idempotency. Same inbox pattern as inventory-service.
     */
    @Column(name = "source_event_id", nullable = false, length = 36)
    private String sourceEventId;

    /**
     * For now: SIMULATED. In a real system: SENT / FAILED / RETRYING.
     */
    @Column(name = "delivery_status", nullable = false, length = 20)
    private String deliveryStatus;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
