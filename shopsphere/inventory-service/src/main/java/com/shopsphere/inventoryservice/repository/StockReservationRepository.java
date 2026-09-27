package com.shopsphere.inventoryservice.repository;

import com.shopsphere.inventoryservice.entity.ReservationStatus;
import com.shopsphere.inventoryservice.entity.StockReservation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    Optional<StockReservation> findByOrderIdAndProductId(Long orderId, Long productId);

    List<StockReservation> findByOrderIdAndStatus(Long orderId, ReservationStatus status);

    /** Orders with at least one overdue hold, oldest order id first. */
    @Query("""
            SELECT DISTINCT r.orderId FROM StockReservation r
            WHERE r.status = :status AND r.reservationExpiresAt <= :now
            ORDER BY r.orderId
            """)
    List<Long> findOrderIdsWithExpiredReservations(@Param("status") ReservationStatus status,
                                                   @Param("now") Instant now,
                                                   Pageable pageable);
}
