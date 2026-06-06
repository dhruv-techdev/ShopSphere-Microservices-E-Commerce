package com.shopsphere.notificationservice.service;

import com.shopsphere.common.events.LowStockEvent;
import com.shopsphere.common.events.OrderCreatedEvent;
import com.shopsphere.common.events.PaymentFailedEvent;
import com.shopsphere.common.events.PaymentSuccessfulEvent;
import com.shopsphere.notificationservice.dto.NotificationResponse;
import com.shopsphere.notificationservice.entity.Notification;
import com.shopsphere.notificationservice.entity.NotificationType;
import com.shopsphere.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /* ---------------------------------------------------------------- */
    /* Event handlers — each idempotent via sourceEventId                */
    /* ---------------------------------------------------------------- */

    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        if (notificationRepository.existsBySourceEventId(event.getEventId())) {
            log.info("Skipping order.created eventId={} — already processed", event.getEventId());
            return;
        }

        String subject = "Order #" + event.getOrderId() + " received";
        String body = String.format(
                "Hi! We've received your order #%d totalling $%s with %d item(s). " +
                        "We'll let you know once payment is confirmed.",
                event.getOrderId(), event.getTotalAmount(), event.getItemCount());

        persistAndSimulateSend(NotificationType.ORDER_PLACED, event.getUserId(),
                event.getOrderId(), subject, body, event.getEventId());
    }

    @Transactional
    public void handlePaymentSuccessful(PaymentSuccessfulEvent event) {
        if (notificationRepository.existsBySourceEventId(event.getEventId())) {
            log.info("Skipping payment.successful eventId={} — already processed", event.getEventId());
            return;
        }

        String subject = "Payment confirmed for order #" + event.getOrderId();
        String body = String.format(
                "Payment of $%s for order #%d has been successfully processed. " +
                        "Reference: %s. We're preparing your order for shipment.",
                event.getAmount(), event.getOrderId(), event.getPaymentReference());

        persistAndSimulateSend(NotificationType.PAYMENT_SUCCESSFUL, event.getUserId(),
                event.getOrderId(), subject, body, event.getEventId());
    }

    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        if (notificationRepository.existsBySourceEventId(event.getEventId())) {
            log.info("Skipping payment.failed eventId={} — already processed", event.getEventId());
            return;
        }

        String subject = "Payment failed for order #" + event.getOrderId();
        String body = String.format(
                "Unfortunately your payment of $%s for order #%d could not be processed. " +
                        "Reason: %s. Your reserved stock has been released and your cart cleared. " +
                        "Please try again with a different payment method.",
                event.getAmount(), event.getOrderId(), event.getReason());

        persistAndSimulateSend(NotificationType.PAYMENT_FAILED, event.getUserId(),
                event.getOrderId(), subject, body, event.getEventId());
    }

    @Transactional
    public void handleLowStock(LowStockEvent event) {
        if (notificationRepository.existsBySourceEventId(event.getEventId())) {
            log.info("Skipping inventory.low-stock eventId={} — already processed", event.getEventId());
            return;
        }

        String subject = "Low stock alert: product " + event.getProductId();
        String body = String.format(
                "Product %d is running low. Sellable quantity: %d (threshold: %d). " +
                        "Available: %d, Reserved: %d.",
                event.getProductId(), event.getSellableQuantity(), event.getThreshold(),
                event.getAvailableQuantity(), event.getReservedQuantity());

        // userId = null — admin-facing notification
        persistAndSimulateSend(NotificationType.LOW_STOCK_ALERT, null,
                null, subject, body, event.getEventId());
    }

    /* ---------------------------------------------------------------- */
    /* Reads                                                             */
    /* ---------------------------------------------------------------- */

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getByUser(Long userId, NotificationType type, Pageable pageable) {
        Page<Notification> page = (type == null)
                ? notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type, pageable);
        return page.map(this::toResponse);
    }

    public long countNotifications() {
        return notificationRepository.count();
    }

    /* ---------------------------------------------------------------- */
    /* Helpers                                                           */
    /* ---------------------------------------------------------------- */

    private void persistAndSimulateSend(NotificationType type, Long userId, Long orderId,
                                        String subject, String body, String sourceEventId) {
        Notification notification = Notification.builder()
                .type(type)
                .userId(userId)
                .orderId(orderId)
                .subject(subject)
                .body(body)
                .sourceEventId(sourceEventId)
                .deliveryStatus("SIMULATED")
                .build();
        Notification saved = notificationRepository.save(notification);

        // The "send" — in a real system, this would call SendGrid / SES / Twilio.
        // Simulating with a structured log line that's grep-able for audits/demos.
        log.info("📨 [SIMULATED SEND] type={} to userId={} orderId={} subject='{}'",
                type, userId, orderId, subject);
        log.debug("📨 [SIMULATED SEND] body: {}", body);
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .userId(n.getUserId())
                .orderId(n.getOrderId())
                .subject(n.getSubject())
                .body(n.getBody())
                .deliveryStatus(n.getDeliveryStatus())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
