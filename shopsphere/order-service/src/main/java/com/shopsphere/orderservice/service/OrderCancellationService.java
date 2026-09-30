package com.shopsphere.orderservice.service;

import com.shopsphere.common.events.OrderCancelledEvent;
import com.shopsphere.common.events.OrderItemSnapshot;
import com.shopsphere.common.events.ReservationExpiredEvent;
import com.shopsphere.orderservice.entity.CancellationReason;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.entity.ProcessedEvent;
import com.shopsphere.orderservice.messaging.OrderEventPublisher;
import com.shopsphere.orderservice.repository.OrderRepository;
import com.shopsphere.orderservice.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

/**
 * US38 — compensation for an order whose stock hold expired before payment settled:
 * PENDING_PAYMENT → CANCELLED (reason RESERVATION_EXPIRED), then order.cancelled.
 *
 * <p>Only unpaid orders are cancelled. If the order has already moved on (e.g. a late
 * payment was committed and the order shipped), the expiry is recorded and ignored.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCancellationService {

    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final OrderEventPublisher orderEventPublisher;

    @Transactional
    public void onReservationExpired(ReservationExpiredEvent event) {
        String eventId = event.getEventId();
        if (eventId == null || eventId.isBlank()) {
            log.warn("Ignoring inventory.reservation-expired for order {} without eventId", event.getOrderId());
            return;
        }
        if (processedEventRepository.existsById(eventId)) {
            log.info("Skipping inventory.reservation-expired eventId={} — already processed", eventId);
            return;
        }

        orderRepository.findById(event.getOrderId()).ifPresentOrElse(
                order -> cancelIfUnpaid(order, CancellationReason.RESERVATION_EXPIRED, eventId),
                () -> log.warn("inventory.reservation-expired eventId={} references unknown order {}",
                        eventId, event.getOrderId()));

        processedEventRepository.save(ProcessedEvent.of(eventId,
                event.getEventType() != null ? event.getEventType() : ReservationExpiredEvent.TYPE));
    }

    private void cancelIfUnpaid(Order order, CancellationReason reason, String sourceEventId) {
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            log.info("Order {} is {} — not cancelling for {} (eventId={})",
                    order.getId(), order.getStatus(), reason, sourceEventId);
            return;
        }

        Instant now = Instant.now();
        order.cancel(reason, now);
        orderRepository.save(order);
        log.info("Order {} CANCELLED — reason={} (eventId={})", order.getId(), reason, sourceEventId);

        // Built inside the transaction (items are lazy); sent only after commit.
        applicationEventPublisher.publishEvent(new OrderCancellationCommitted(buildEvent(order, reason, now)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancellationCommitted(OrderCancellationCommitted committed) {
        orderEventPublisher.publishOrderCancelled(committed.event());
    }

    /** Internal Spring event wrapper. Package-private so unit tests can inspect the payload. */
    record OrderCancellationCommitted(OrderCancelledEvent event) {}

    private static OrderCancelledEvent buildEvent(Order order, CancellationReason reason, Instant at) {
        return OrderCancelledEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(OrderCancelledEvent.TYPE)
                .occurredAt(at)
                .orderId(order.getId())
                .userId(order.getUserId())
                .reason(reason.name())
                .reasonDescription(reason.getDescription())
                .cancelledAt(at)
                .totalAmount(order.getTotalAmount())
                .items(order.getItems().stream()
                        .map(i -> OrderItemSnapshot.builder()
                                .productId(i.getProductId())
                                .quantity(i.getQuantity())
                                .unitPrice(i.getUnitPrice())
                                .build())
                        .toList())
                .build();
    }
}
