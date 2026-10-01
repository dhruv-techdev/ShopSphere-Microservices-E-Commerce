package com.shopsphere.notificationservice.service;

import com.shopsphere.notificationservice.dto.AdminNotificationResponse;
import com.shopsphere.notificationservice.dto.NotificationSummaryResponse;
import com.shopsphere.notificationservice.entity.DeliveryStatus;
import com.shopsphere.notificationservice.entity.Notification;
import com.shopsphere.notificationservice.entity.NotificationType;
import com.shopsphere.notificationservice.exception.NotificationNotFoundException;
import com.shopsphere.notificationservice.exception.NotificationRetryNotAllowedException;
import com.shopsphere.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** US46 — notification log for the admin console, plus manual retry of FAILED emails. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminNotificationService {

    private final NotificationRepository repository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<AdminNotificationResponse> search(DeliveryStatus status, NotificationType type, Long userId,
                                                  Long orderId, String recipient, Pageable pageable) {
        Specification<Notification> spec = (root, query, cb) -> cb.conjunction();
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("deliveryStatus"), status));
        }
        if (type != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
        }
        if (userId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("userId"), userId));
        }
        if (orderId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("orderId"), orderId));
        }
        if (recipient != null && !recipient.isBlank()) {
            String like = "%" + recipient.trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.<String>get("recipient")), like, '\\'));
        }
        return repository.findAll(spec, pageable).map(AdminNotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminNotificationResponse get(Long id) {
        return AdminNotificationResponse.from(load(id));
    }

    @Transactional(readOnly = true)
    public NotificationSummaryResponse summary() {
        Map<DeliveryStatus, Long> byStatus = new LinkedHashMap<>();
        for (DeliveryStatus s : DeliveryStatus.values()) {
            byStatus.put(s, 0L);
        }
        repository.statusTotals().forEach(t -> byStatus.put(t.getStatus(), t.getTotal()));
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        return new NotificationSummaryResponse(total, byStatus);
    }

    /**
     * Puts a FAILED notification back in the retry queue, due now; NotificationRetryScheduler sends it on
     * its next poll. One-time-link emails are refused (the link has likely expired — the user should
     * request a new one), and RETRYING ones are already scheduled.
     */
    @Transactional
    public AdminNotificationResponse retry(Long id) {
        Notification n = load(id);
        if (AdminNotificationResponse.ONE_TIME_LINK_TYPES.contains(n.getType())) {
            throw new NotificationRetryNotAllowedException(
                    "Notification " + id + " contains a one-time link; ask the user to request a new one instead");
        }
        if (n.getDeliveryStatus() != DeliveryStatus.FAILED) {
            throw new NotificationRetryNotAllowedException(
                    "Notification " + id + " is " + n.getDeliveryStatus() + "; only FAILED notifications can be retried");
        }
        int updated = repository.requeue(id, DeliveryStatus.FAILED, DeliveryStatus.RETRYING, clock.instant());
        if (updated == 0) {
            throw new NotificationRetryNotAllowedException("Notification " + id + " changed while retrying; refresh and try again");
        }
        log.info("AUDIT notification {} ({}) requeued by admin after {} attempt(s)", id, n.getType(), n.getAttempts());
        return AdminNotificationResponse.from(load(id));
    }

    private Notification load(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotificationNotFoundException(id));
    }
}
