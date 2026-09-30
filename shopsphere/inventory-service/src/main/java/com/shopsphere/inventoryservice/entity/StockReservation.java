package com.shopsphere.inventoryservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * US37 — one hold per (order, product). Inventory.reservedQuantity is the sum of RESERVED rows;
 * this table is what lets a hold expire individually.
 *
 * <p>{@code @Version} plus Inventory's own {@code @Version} make concurrent expiry safe: two
 * sweepers (or a sweeper and a payment) touching the same hold can't both win.</p>
 */
@Entity
@Table(name = "stock_reservations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_reservations_order_product",
                columnNames = {"order_id", "product_id"}),
        indexes = {
                @Index(name = "idx_stock_reservations_status_expires", columnList = "status, reservation_expires_at"),
                @Index(name = "idx_stock_reservations_order", columnList = "order_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    /** When this hold is released if payment hasn't settled. */
    @Column(name = "reservation_expires_at", nullable = false)
    private Instant reservationExpiresAt;

    /** When the hold left RESERVED (committed / released / expired). */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public boolean isExpiredAt(Instant now) {
        return status == ReservationStatus.RESERVED && !reservationExpiresAt.isAfter(now);
    }

    public void markExpired(Instant at) {
        requireReserved("expire");
        status = ReservationStatus.EXPIRED;
        resolvedAt = at;
    }

    public void markReleased(Instant at) {
        requireReserved("release");
        status = ReservationStatus.RELEASED;
        resolvedAt = at;
    }

    /** Allowed from RESERVED, and from EXPIRED/RELEASED for a late payment. */
    public void markCommitted(Instant at) {
        if (status == ReservationStatus.COMMITTED) {
            throw new IllegalStateException("Reservation " + id + " is already COMMITTED");
        }
        status = ReservationStatus.COMMITTED;
        resolvedAt = at;
    }

    private void requireReserved(String action) {
        if (status != ReservationStatus.RESERVED) {
            throw new IllegalStateException("Cannot " + action + " reservation " + id + " in status " + status);
        }
    }
}
