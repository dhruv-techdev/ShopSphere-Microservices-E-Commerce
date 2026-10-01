package com.shopsphere.orderservice.service;

import com.shopsphere.common.events.PaymentFailedEvent;
import com.shopsphere.common.events.PaymentSuccessfulEvent;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.entity.ProcessedEvent;
import com.shopsphere.orderservice.repository.OrderRepository;
import com.shopsphere.orderservice.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * US45 — applies payment-service outcomes to orders:
 * PENDING_PAYMENT → PAID on payment.successful, PENDING_PAYMENT → PAYMENT_FAILED on payment.failed.
 *
 * <p>Idempotent per eventId (processed_events). Payment details are recorded even when the
 * status can't move — e.g. a payment that settles after an admin cancelled the order is kept
 * so the admin app can flag the refund — but a successful payment is never overwritten by a
 * later failure.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderPaymentService {

    static final int FAILURE_REASON_MAX_LENGTH = 255;

    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void onPaymentSuccessful(PaymentSuccessfulEvent event) {
        if (!shouldProcess(event.getEventId(), "payment.successful")) {
            return;
        }

        orderRepository.findById(event.getOrderId()).ifPresentOrElse(order -> {
            if (order.getPaidAt() == null) {
                order.setPaidAt(atOrNow(event.getOccurredAt()));
                order.setPaymentReference(event.getPaymentReference());
            }

            OrderStatus current = order.getStatus();
            if (current == OrderStatus.PENDING_PAYMENT) {
                order.setStatus(OrderStatus.PAID);
                log.info("Order {} PENDING_PAYMENT -> PAID (payment {}, eventId={})",
                        order.getId(), event.getPaymentReference(), event.getEventId());
            } else if (current == OrderStatus.CANCELLED) {
                log.warn("Order {} was CANCELLED before payment {} settled — refund required (eventId={})",
                        order.getId(), event.getPaymentReference(), event.getEventId());
            } else {
                log.info("Order {} is {} — payment {} recorded without a status change (eventId={})",
                        order.getId(), current, event.getPaymentReference(), event.getEventId());
            }
            orderRepository.save(order);
        }, () -> log.warn("payment.successful eventId={} references unknown order {}",
                event.getEventId(), event.getOrderId()));

        markProcessed(event.getEventId(), event.getEventType(), PaymentSuccessfulEvent.TYPE);
    }

    @Transactional
    public void onPaymentFailed(PaymentFailedEvent event) {
        if (!shouldProcess(event.getEventId(), "payment.failed")) {
            return;
        }

        orderRepository.findById(event.getOrderId()).ifPresentOrElse(order -> {
            if (order.getPaidAt() != null) {
                log.warn("Ignoring payment.failed eventId={} for order {} — it is already paid ({})",
                        event.getEventId(), order.getId(), order.getPaymentReference());
                return;
            }
            if (order.getPaymentFailedAt() == null) {
                order.setPaymentFailedAt(atOrNow(event.getOccurredAt()));
                order.setPaymentFailureReason(truncate(event.getReason()));
                order.setPaymentReference(event.getPaymentReference());
            }
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                order.setStatus(OrderStatus.PAYMENT_FAILED);
                log.info("Order {} PENDING_PAYMENT -> PAYMENT_FAILED ({}; eventId={})",
                        order.getId(), event.getReason(), event.getEventId());
            } else {
                log.info("Order {} is {} — payment failure recorded without a status change (eventId={})",
                        order.getId(), order.getStatus(), event.getEventId());
            }
            orderRepository.save(order);
        }, () -> log.warn("payment.failed eventId={} references unknown order {}",
                event.getEventId(), event.getOrderId()));

        markProcessed(event.getEventId(), event.getEventType(), PaymentFailedEvent.TYPE);
    }

    /* ---------------------------------------------------------------- */

    private boolean shouldProcess(String eventId, String topic) {
        if (eventId == null || eventId.isBlank()) {
            log.warn("Ignoring {} without eventId", topic);
            return false;
        }
        if (processedEventRepository.existsById(eventId)) {
            log.info("Skipping {} eventId={} — already processed", topic, eventId);
            return false;
        }
        return true;
    }

    private void markProcessed(String eventId, String eventType, String fallbackType) {
        processedEventRepository.save(ProcessedEvent.of(eventId, eventType != null ? eventType : fallbackType));
    }

    private static Instant atOrNow(Instant occurredAt) {
        return occurredAt != null ? occurredAt : Instant.now();
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() > FAILURE_REASON_MAX_LENGTH ? reason.substring(0, FAILURE_REASON_MAX_LENGTH) : reason;
    }
}
