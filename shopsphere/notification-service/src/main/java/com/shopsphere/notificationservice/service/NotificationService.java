package com.shopsphere.notificationservice.service;

import com.shopsphere.common.events.LowStockEvent;
import com.shopsphere.common.events.OrderCancelledEvent;
import com.shopsphere.common.events.OrderCreatedEvent;
import com.shopsphere.common.events.PaymentFailedEvent;
import com.shopsphere.common.events.PaymentSuccessfulEvent;
import com.shopsphere.common.events.ShipmentDeliveredEvent;
import com.shopsphere.common.events.ShipmentDispatchedEvent;
import com.shopsphere.notificationservice.delivery.NotificationDispatcher;
import com.shopsphere.notificationservice.delivery.NotificationDraft;
import com.shopsphere.notificationservice.dto.NotificationResponse;
import com.shopsphere.notificationservice.entity.Notification;
import com.shopsphere.notificationservice.entity.NotificationType;
import com.shopsphere.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns domain events into notification drafts. Idempotency, delivery and status tracking
 * live in {@link NotificationDispatcher} (US39). Handlers are deliberately NOT transactional:
 * the email is sent between two short DB transactions, never inside one.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final NotificationRepository notificationRepository;
    private final NotificationDispatcher dispatcher;

    /* ---------------------------------------------------------------- */
    /* Event handlers                                                    */
    /* ---------------------------------------------------------------- */

    public void handleOrderCreated(OrderCreatedEvent event) {
        String subject = "Order #" + event.getOrderId() + " received";
        String body = String.format(
                "Hi! We've received your order #%d totalling $%s with %d item(s). " +
                        "We'll let you know once payment is confirmed.",
                event.getOrderId(), event.getTotalAmount(), event.getItemCount());

        dispatcher.deliver(new NotificationDraft(NotificationType.ORDER_PLACED, event.getUserId(),
                event.getOrderId(), subject, body, event.getEventId(),
                model("totalAmount", event.getTotalAmount(), "itemCount", event.getItemCount())));
    }

    public void handleOrderCancelled(OrderCancelledEvent event) {
        String why = event.getReasonDescription() != null
                ? event.getReasonDescription()
                : "It could not be completed.";

        String subject = "Order #" + event.getOrderId() + " has been cancelled";
        String body = String.format(
                "We're sorry — your order #%d (total $%s) was cancelled on %s. %s " +
                        "You're welcome to place the order again.",
                event.getOrderId(), event.getTotalAmount(), when(event.getCancelledAt()), why);

        dispatcher.deliver(new NotificationDraft(NotificationType.ORDER_CANCELLED, event.getUserId(),
                event.getOrderId(), subject, body, event.getEventId(),
                model("totalAmount", event.getTotalAmount(),
                        "reasonDescription", why,
                        "cancelledAt", when(event.getCancelledAt()))));
    }

    public void handlePaymentSuccessful(PaymentSuccessfulEvent event) {
        String subject = "Payment confirmed for order #" + event.getOrderId();
        String body = String.format(
                "Payment of $%s for order #%d has been successfully processed. " +
                        "Reference: %s. We're preparing your order for shipment.",
                event.getAmount(), event.getOrderId(), event.getPaymentReference());

        dispatcher.deliver(new NotificationDraft(NotificationType.PAYMENT_SUCCESSFUL, event.getUserId(),
                event.getOrderId(), subject, body, event.getEventId(),
                model("amount", event.getAmount(), "paymentReference", event.getPaymentReference())));
    }

    public void handlePaymentFailed(PaymentFailedEvent event) {
        String subject = "Payment failed for order #" + event.getOrderId();
        String body = String.format(
                "Unfortunately your payment of $%s for order #%d could not be processed. " +
                        "Reason: %s. Your reserved stock has been released and your cart cleared. " +
                        "Please try again with a different payment method.",
                event.getAmount(), event.getOrderId(), event.getReason());

        dispatcher.deliver(new NotificationDraft(NotificationType.PAYMENT_FAILED, event.getUserId(),
                event.getOrderId(), subject, body, event.getEventId(),
                model("amount", event.getAmount(), "reason", event.getReason())));
    }

    public void handleLowStock(LowStockEvent event) {
        String subject = "Low stock alert: product " + event.getProductId();
        String body = String.format(
                "Product %d is running low. Sellable quantity: %d (threshold: %d). " +
                        "Available: %d, Reserved: %d.",
                event.getProductId(), event.getSellableQuantity(), event.getThreshold(),
                event.getAvailableQuantity(), event.getReservedQuantity());

        // userId = null → admin address
        dispatcher.deliver(new NotificationDraft(NotificationType.LOW_STOCK_ALERT, null,
                null, subject, body, event.getEventId(),
                model("productId", event.getProductId(),
                        "sellableQuantity", event.getSellableQuantity(),
                        "threshold", event.getThreshold(),
                        "availableQuantity", event.getAvailableQuantity(),
                        "reservedQuantity", event.getReservedQuantity())));
    }

    public void handleShipmentDispatched(ShipmentDispatchedEvent event) {
        String destination = event.getShippingAddress() == null
                ? "your shipping address"
                : event.getShippingAddress().getCity() + ", " + event.getShippingAddress().getCountry();

        String subject = "Order #" + event.getOrderId() + " has shipped";
        String body = String.format(
                "Good news! Your order #%d is on its way to %s with %s. " +
                        "Tracking number: %s. Shipped on %s.",
                event.getOrderId(), destination, event.getCarrier(),
                event.getTrackingNumber(), when(event.getShippedAt()));

        dispatcher.deliver(new NotificationDraft(NotificationType.SHIPMENT_DISPATCHED, event.getUserId(),
                event.getOrderId(), subject, body, event.getEventId(),
                model("carrier", event.getCarrier(),
                        "trackingNumber", event.getTrackingNumber(),
                        "destination", destination,
                        "shippedAt", when(event.getShippedAt()))));
    }

    public void handleShipmentDelivered(ShipmentDeliveredEvent event) {
        String subject = "Order #" + event.getOrderId() + " has been delivered";
        String body = String.format(
                "Your order #%d was delivered on %s (tracking number %s). We hope you enjoy it!",
                event.getOrderId(), when(event.getDeliveredAt()), event.getTrackingNumber());

        dispatcher.deliver(new NotificationDraft(NotificationType.SHIPMENT_DELIVERED, event.getUserId(),
                event.getOrderId(), subject, body, event.getEventId(),
                model("trackingNumber", event.getTrackingNumber(),
                        "deliveredAt", when(event.getDeliveredAt()))));
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

    static String when(Instant instant) {
        return instant == null ? "just now" : WHEN.format(instant);
    }

    /** Ordered map that tolerates null values (Map.of doesn't). */
    private static Map<String, Object> model(Object... keyValues) {
        Map<String, Object> model = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            model.put((String) keyValues[i], keyValues[i + 1]);
        }
        return model;
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
                .channel(n.getChannel())
                .sentAt(n.getSentAt())
                .failureReason(n.getFailureReason())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
