package com.shopsphere.inventoryservice.service;

import com.shopsphere.inventoryservice.entity.ReservationStatus;
import com.shopsphere.inventoryservice.repository.StockReservationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * US37 — periodically releases overdue holds. Safe to run on every instance: each order is
 * expired in its own optimistic transaction, so racing sweepers can't double-release.
 */
@Component
@Slf4j
public class ReservationExpirySweeper {

    private final StockReservationRepository reservationRepository;
    private final ReservationExpiryService expiryService;
    private final Clock clock;
    private final int batchSize;

    public ReservationExpirySweeper(StockReservationRepository reservationRepository,
                                    ReservationExpiryService expiryService,
                                    Clock clock,
                                    @Value("${app.inventory.reservation.sweep-batch-size:100}") int batchSize) {
        this.reservationRepository = reservationRepository;
        this.expiryService = expiryService;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.inventory.reservation.sweep-interval-ms:60000}",
            initialDelayString = "${app.inventory.reservation.sweep-initial-delay-ms:30000}")
    public void scheduledSweep() {
        sweep();
    }

    /** @return number of orders whose holds were expired in this pass. */
    public int sweep() {
        List<Long> orderIds = reservationRepository.findOrderIdsWithExpiredReservations(
                ReservationStatus.RESERVED, clock.instant(), PageRequest.of(0, batchSize));
        if (orderIds.isEmpty()) {
            return 0;
        }

        int expired = 0;
        int conflicts = 0;
        for (Long orderId : orderIds) {
            try {
                if (expiryService.expireOrder(orderId)) {
                    expired++;
                }
            } catch (OptimisticLockingFailureException ex) {
                // Someone else touched the stock/hold first — re-evaluated on the next pass.
                conflicts++;
                log.info("Optimistic lock conflict expiring order {} — will retry next sweep", orderId);
            } catch (Exception ex) {
                log.error("Failed to expire reservations for order {}: {}", orderId, ex.getMessage(), ex);
            }
        }

        log.info("Reservation sweep: {} candidate order(s), {} expired, {} conflict(s)",
                orderIds.size(), expired, conflicts);
        return expired;
    }
}
