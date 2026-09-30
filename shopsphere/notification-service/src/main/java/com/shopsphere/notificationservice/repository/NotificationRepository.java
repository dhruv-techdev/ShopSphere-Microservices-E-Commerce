package com.shopsphere.notificationservice.repository;

import com.shopsphere.notificationservice.entity.DeliveryStatus;
import com.shopsphere.notificationservice.entity.Notification;
import com.shopsphere.notificationservice.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsBySourceEventId(String sourceEventId);

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(
            Long userId, NotificationType type, Pageable pageable);

    /** For admin queries — LOW_STOCK_ALERT and similar where userId is null. */
    List<Notification> findByTypeOrderByCreatedAtDesc(NotificationType type);

    /* ---------------- US40 — retries ---------------- */

    long countByDeliveryStatus(DeliveryStatus status);

    @Query("""
            SELECT n FROM Notification n
            WHERE n.deliveryStatus = :status AND n.nextAttemptAt <= :now
            ORDER BY n.nextAttemptAt
            """)
    List<Notification> findDue(@Param("status") DeliveryStatus status,
                               @Param("now") Instant now,
                               Pageable pageable);

    /**
     * Compare-and-set claim: only one poller instance can move next_attempt_at from the value
     * it read to a lease in the future. Returns 1 if this caller won, 0 otherwise.
     */
    @Modifying
    @Query("""
            UPDATE Notification n SET n.nextAttemptAt = :leaseUntil
            WHERE n.id = :id AND n.deliveryStatus = :status AND n.nextAttemptAt = :expected
            """)
    int claim(@Param("id") Long id,
              @Param("status") DeliveryStatus status,
              @Param("expected") Instant expected,
              @Param("leaseUntil") Instant leaseUntil);
}
