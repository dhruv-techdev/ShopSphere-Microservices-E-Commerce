package com.shopsphere.paymentservice.repository;

import com.shopsphere.paymentservice.entity.Payment;
import com.shopsphere.paymentservice.entity.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByPaymentReference(String paymentReference);

    /** Multiple payments per order are possible (retries after failure). Most recent first. */
    List<Payment> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    List<Payment> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, PaymentStatus status);

    /* ---------------- US46 — reconciliation ---------------- */

    List<Payment> findByOrderIdInAndStatusOrderByCreatedAtAsc(Collection<Long> orderIds, PaymentStatus status);

    @Query("""
            SELECT p.status AS status, COUNT(p) AS paymentCount, SUM(p.amount) AS amount
            FROM Payment p
            WHERE p.createdAt >= :from AND p.createdAt < :to
            GROUP BY p.status
            """)
    List<StatusTotal> totalsByStatus(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            SELECT p.orderId AS orderId, MIN(p.userId) AS userId, COUNT(p) AS successfulPayments,
                   SUM(p.amount) AS totalCaptured, MAX(p.createdAt) AS lastCapturedAt
            FROM Payment p
            WHERE p.status = :status
            GROUP BY p.orderId
            HAVING COUNT(p) > 1
            ORDER BY MAX(p.createdAt) DESC
            """)
    List<DuplicateCapture> findDuplicateCaptures(@Param("status") PaymentStatus status, Pageable pageable);

    @Query("""
            SELECT COUNT(DISTINCT p.orderId) FROM Payment p
            WHERE p.status = :status AND p.orderId IN (
                SELECT p2.orderId FROM Payment p2 WHERE p2.status = :status
                GROUP BY p2.orderId HAVING COUNT(p2) > 1)
            """)
    long countOrdersCapturedMoreThanOnce(@Param("status") PaymentStatus status);

    interface StatusTotal {
        PaymentStatus getStatus();
        Long getPaymentCount();
        BigDecimal getAmount();
    }

    interface DuplicateCapture {
        Long getOrderId();
        Long getUserId();
        Long getSuccessfulPayments();
        BigDecimal getTotalCaptured();
        Instant getLastCapturedAt();
    }
}
