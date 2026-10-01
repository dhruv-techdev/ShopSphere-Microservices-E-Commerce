package com.shopsphere.orderservice.entity;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_user", columnList = "user_id"),
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "item_count", nullable = false)
    private Integer itemCount;

    /**
     * US33 — where the order ships. Columns are prefixed with {@code shipping_} so a
     * billing address can be embedded later without clashes. Null only for legacy orders.
     */
    @Embedded
    @Valid
    @AttributeOverrides({
            @AttributeOverride(name = "recipientName", column = @Column(name = "shipping_recipient_name", length = 100)),
            @AttributeOverride(name = "phone",         column = @Column(name = "shipping_phone", length = 20)),
            @AttributeOverride(name = "line1",         column = @Column(name = "shipping_line1", length = 200)),
            @AttributeOverride(name = "line2",         column = @Column(name = "shipping_line2", length = 200)),
            @AttributeOverride(name = "city",          column = @Column(name = "shipping_city", length = 100)),
            @AttributeOverride(name = "state",         column = @Column(name = "shipping_state", length = 100)),
            @AttributeOverride(name = "postalCode",    column = @Column(name = "shipping_postal_code", length = 20)),
            @AttributeOverride(name = "country",       column = @Column(name = "shipping_country", length = 2))
    })
    private Address shippingAddress;

    /* ---------- US36: shipment tracking, filled from shipping-service events ---------- */

    @Column(name = "shipment_id")
    private Long shipmentId;

    @Column(name = "carrier", length = 100)
    private String carrier;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    /* ---------- US38: cancellation ---------- */

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason", length = 40)
    private CancellationReason cancellationReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /* ---------- US45: payment outcome, filled from payment-service events ---------- */

    @Column(name = "payment_reference", length = 64)
    private String paymentReference;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "payment_failed_at")
    private Instant paymentFailedAt;

    @Column(name = "payment_failure_reason", length = 255)
    private String paymentFailureReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Convenience method to keep both sides of the relationship in sync. */
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    /** US38 — moves the order to CANCELLED, recording why and when. */
    public void cancel(CancellationReason reason, Instant at) {
        if (!status.canTransitionTo(OrderStatus.CANCELLED)) {
            throw new IllegalStateException("Order " + id + " cannot be cancelled from status " + status);
        }
        this.status = OrderStatus.CANCELLED;
        this.cancellationReason = reason;
        this.cancelledAt = at;
    }

    /**
     * US45 — payment outcome. Shipped/delivered orders were necessarily paid (a shipment only
     * exists after payment.successful), which also covers orders from before payment tracking.
     * Not a JavaBean getter on purpose, so JPA never treats it as a column.
     */
    public PaymentState paymentState() {
        if (paidAt != null || status == OrderStatus.PAID
                || status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED) {
            return PaymentState.PAID;
        }
        if (paymentFailedAt != null || status == OrderStatus.PAYMENT_FAILED) {
            return PaymentState.FAILED;
        }
        return PaymentState.PENDING;
    }
}
