package com.shopsphere.orderservice.service;

import com.shopsphere.orderservice.dto.AdminOrderSummaryResponse;
import com.shopsphere.orderservice.dto.OrderResponse;
import com.shopsphere.orderservice.dto.PaymentIssueResponse;
import com.shopsphere.orderservice.entity.CancellationReason;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.exception.OrderNotFoundException;
import com.shopsphere.orderservice.exception.OrderStatusConflictException;
import com.shopsphere.orderservice.repository.OrderRepository;
import com.shopsphere.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** US42 — order management across all customers. Callers are ADMIN (enforced on the controller). */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminOrderService {

    /** US46 — cap per issue type so a backlog can't produce a huge response. */
    static final int RECONCILIATION_LIMIT = 200;

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional(readOnly = true)
    public Page<AdminOrderSummaryResponse> search(OrderStatus status, Long userId, Pageable pageable) {
        Specification<Order> spec = (root, query, cb) -> cb.conjunction();
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (userId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("userId"), userId));
        }
        return orderRepository.findAll(spec, pageable).map(AdminOrderSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long orderId) {
        return orderService.toResponse(load(orderId));
    }

    /**
     * Cancels an order that hasn't shipped. Idempotent: cancelling a cancelled order returns it unchanged.
     * Publishes order.cancelled after commit (the customer is notified via notification-service).
     */
    @Transactional
    public OrderResponse cancel(Long orderId, AuthenticatedUser admin) {
        Order order = load(orderId);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return orderService.toResponse(order);
        }
        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            throw new OrderStatusConflictException(orderId, order.getStatus(), "cancelled");
        }

        OrderStatus previous = order.getStatus();
        Instant now = Instant.now();
        order.cancel(CancellationReason.ADMIN_CANCELLED, now);
        orderRepository.save(order);

        applicationEventPublisher.publishEvent(new OrderCancellationService.OrderCancellationCommitted(
                OrderCancelledEvents.from(order, CancellationReason.ADMIN_CANCELLED, now)));

        log.info("AUDIT order {} {} -> CANCELLED by admin userId={} email={}",
                orderId, previous,
                admin == null ? null : admin.userId(),
                admin == null ? null : admin.email());
        return orderService.toResponse(order);
    }

    /**
     * US46 — orders whose status and payment disagree: cancelled-but-paid (refund),
     * failed-but-later-paid, and orders stuck awaiting payment longer than {@code overdueAfter}.
     */
    @Transactional(readOnly = true)
    public List<PaymentIssueResponse> reconciliation(Duration overdueAfter) {
        return reconciliation(overdueAfter, Instant.now());
    }

    List<PaymentIssueResponse> reconciliation(Duration overdueAfter, Instant now) {
        Pageable limit = PageRequest.of(0, RECONCILIATION_LIMIT);
        List<PaymentIssueResponse> issues = new ArrayList<>();

        orderRepository.findByStatusAndPaidAtIsNotNullOrderByUpdatedAtDesc(OrderStatus.CANCELLED, limit)
                .forEach(o -> issues.add(PaymentIssueResponse.of(o, PaymentIssueResponse.Issue.REFUND_REQUIRED,
                        o.getCancelledAt() != null ? o.getCancelledAt() : o.getUpdatedAt())));

        orderRepository.findByStatusAndPaidAtIsNotNullOrderByUpdatedAtDesc(OrderStatus.PAYMENT_FAILED, limit)
                .forEach(o -> issues.add(PaymentIssueResponse.of(o, PaymentIssueResponse.Issue.PAID_AFTER_FAILURE,
                        o.getPaidAt())));

        orderRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                        OrderStatus.PENDING_PAYMENT, now.minus(overdueAfter), limit)
                .forEach(o -> issues.add(PaymentIssueResponse.of(o, PaymentIssueResponse.Issue.PAYMENT_OVERDUE,
                        o.getCreatedAt())));

        return issues;
    }

    private Order load(Long orderId) {
        return orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
