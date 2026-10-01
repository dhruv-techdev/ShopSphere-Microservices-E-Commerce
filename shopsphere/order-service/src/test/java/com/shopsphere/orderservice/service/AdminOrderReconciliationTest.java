package com.shopsphere.orderservice.service;

import com.shopsphere.orderservice.dto.PaymentIssueResponse;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrderReconciliationTest {

    @Mock OrderRepository orderRepository;
    @Mock OrderService orderService;
    @Mock ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks AdminOrderService service;

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    private static Order order(long id, OrderStatus status) {
        return Order.builder().id(id).userId(7L).status(status).totalAmount(new BigDecimal("50.00")).itemCount(1)
                .createdAt(NOW.minus(Duration.ofHours(2))).build();
    }

    @Test
    void reportsRefundsPaidAfterFailureAndOverduePayments() {
        Order cancelledPaid = order(1, OrderStatus.CANCELLED);
        cancelledPaid.setPaidAt(NOW.minus(Duration.ofHours(1)));
        cancelledPaid.setCancelledAt(NOW.minus(Duration.ofMinutes(30)));
        cancelledPaid.setPaymentReference("ref-1");

        Order failedThenPaid = order(2, OrderStatus.PAYMENT_FAILED);
        failedThenPaid.setPaidAt(NOW.minus(Duration.ofMinutes(10)));

        Order overdue = order(3, OrderStatus.PENDING_PAYMENT);

        when(orderRepository.findByStatusAndPaidAtIsNotNullOrderByUpdatedAtDesc(eq(OrderStatus.CANCELLED), any()))
                .thenReturn(List.of(cancelledPaid));
        when(orderRepository.findByStatusAndPaidAtIsNotNullOrderByUpdatedAtDesc(eq(OrderStatus.PAYMENT_FAILED), any()))
                .thenReturn(List.of(failedThenPaid));
        when(orderRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(OrderStatus.PENDING_PAYMENT), eq(NOW.minus(Duration.ofMinutes(30))), any()))
                .thenReturn(List.of(overdue));

        List<PaymentIssueResponse> issues = service.reconciliation(Duration.ofMinutes(30), NOW);

        assertThat(issues).extracting(PaymentIssueResponse::issue).containsExactly(
                PaymentIssueResponse.Issue.REFUND_REQUIRED,
                PaymentIssueResponse.Issue.PAID_AFTER_FAILURE,
                PaymentIssueResponse.Issue.PAYMENT_OVERDUE);
        assertThat(issues.get(0).since()).isEqualTo(cancelledPaid.getCancelledAt());
        assertThat(issues.get(0).paymentReference()).isEqualTo("ref-1");
        assertThat(issues.get(1).since()).isEqualTo(failedThenPaid.getPaidAt());
        assertThat(issues.get(2).since()).isEqualTo(overdue.getCreatedAt());
    }

    @Test
    void cleanBooks_returnNothing() {
        when(orderRepository.findByStatusAndPaidAtIsNotNullOrderByUpdatedAtDesc(any(), any())).thenReturn(List.of());
        when(orderRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(any(), any(), any())).thenReturn(List.of());

        assertThat(service.reconciliation(Duration.ofMinutes(30), NOW)).isEmpty();
    }
}
