package com.shopsphere.inventoryservice.service;

import com.shopsphere.common.events.OrderItemSnapshot;
import com.shopsphere.common.events.ReservationExpiredEvent;
import com.shopsphere.inventoryservice.entity.Inventory;
import com.shopsphere.inventoryservice.entity.ReservationStatus;
import com.shopsphere.inventoryservice.entity.StockReservation;
import com.shopsphere.inventoryservice.exception.InventoryNotFoundException;
import com.shopsphere.inventoryservice.messaging.InventoryEventPublisher;
import com.shopsphere.inventoryservice.repository.InventoryRepository;
import com.shopsphere.inventoryservice.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * US37 — releases overdue stock holds, one order per transaction.
 *
 * <p>Concurrency is handled optimistically: Inventory and StockReservation both carry
 * {@code @Version}. If another transaction (a payment, a restock, a second sweeper instance)
 * changed either row since we read it, the flush fails with an
 * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}, the whole order's
 * expiry rolls back, no event is published, and the next sweep re-evaluates from fresh state.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationExpiryService {

    private final StockReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryEventPublisher eventPublisher;
    private final Clock clock;

    /** @return true if at least one hold for the order was expired. */
    @Transactional
    public boolean expireOrder(Long orderId) {
        Instant now = clock.instant();
        List<StockReservation> overdue = reservationRepository
                .findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED).stream()
                .filter(r -> r.isExpiredAt(now))
                .toList();
        return applyExpiry(orderId, overdue, now);
    }

    /**
     * Applies expiry to reservations already loaded in the caller's transaction.
     * Public (MANDATORY) so tests can interleave a concurrent writer between read and write.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean applyExpiry(Long orderId, List<StockReservation> overdue, Instant now) {
        if (overdue.isEmpty()) {
            return false;
        }

        for (StockReservation reservation : overdue) {
            // Plain read (no FOR UPDATE): rely on @Version to detect concurrent changes.
            Inventory inv = inventoryRepository.findByProductId(reservation.getProductId())
                    .orElseThrow(() -> new InventoryNotFoundException(reservation.getProductId()));

            inv.setReservedQuantity(Math.max(0, inv.getReservedQuantity() - reservation.getQuantity()));
            reservation.markExpired(now);

            reservationRepository.saveAndFlush(reservation);
            inventoryRepository.saveAndFlush(inv);

            log.info("Expired hold of {} x product {} for order {} (was due {})",
                    reservation.getQuantity(), reservation.getProductId(), orderId,
                    reservation.getReservationExpiresAt());
        }

        ReservationExpiredEvent event = ReservationExpiredEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(ReservationExpiredEvent.TYPE)
                .occurredAt(now)
                .orderId(orderId)
                .expiredAt(now)
                .items(overdue.stream()
                        .map(r -> OrderItemSnapshot.builder()
                                .productId(r.getProductId())
                                .quantity(r.getQuantity())
                                .build())
                        .toList())
                .build();

        // Only tell the world once the release is durable.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventPublisher.publishReservationExpired(event);
            }
        });
        return true;
    }
}
