package com.shopsphere.notificationservice.repository;

import com.shopsphere.notificationservice.entity.Notification;
import com.shopsphere.notificationservice.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsBySourceEventId(String sourceEventId);

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(
            Long userId, NotificationType type, Pageable pageable);

    /** For admin queries — LOW_STOCK_ALERT and similar where userId is null. */
    List<Notification> findByTypeOrderByCreatedAtDesc(NotificationType type);
}
