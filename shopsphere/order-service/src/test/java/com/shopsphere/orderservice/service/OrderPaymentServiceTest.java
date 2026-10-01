package com.shopsphere.orderservice.service;

import com.shopsphere.common.events.PaymentFailedEvent;
import com.shopsphere.common.events.PaymentSuccessfulEvent;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.entity.PaymentState;
import com.shopsphere.orderservice.entity.ProcessedEvent;
import com.shopsphere.orderservice.repository.OrderRepository;
import com.shopsphere.orderservice.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPaymentServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock ProcessedEventRepository processedEventRepository;

    @InjectMocks OrderPaymentService service;

    private static final Instant PAID_AT = Instant.parse("2026-09-28T14:01:00Z");

    private static Order order(OrderStatus status) {
        return Order.builder()
                .id(42L).userId(7L).status(status)
                .totalAmount(new BigDecimal("100.00")).itemCount(2)
                .build();
    }

    private static PaymentSuccessfulEvent successful(String eventId) {
        return PaymentSuccessfulEvent.builder()
                .eventId(eventId).eventType(PaymentSuccessfulEvent.TYPE).occurredAt(PAID_AT)
                .orderId(42L).userId(7L).paymentReference("pay-ref-1").amount(new BigDecimal("100.00"))
                .build();
    }

    private static PaymentFailedEvent failed(String eventId, String reason) {
        return PaymentFailedEvent.builder()
                .eventId(eventId).eventType(PaymentFailedEvent.TYPE).occurredAt(PAID_AT)
                .orderId(42L).userId(7L).paymentReference("pay-ref-2").amount(new BigDecimal("100.00"))
                .reason(reason)
                .build();
    }

    @Test
    void successful_movesPendingOrderToPaid_andRecordsThePayment() {
        Order order = order(OrderStatus.PENDING_PAYMENT);
        when(processedEventRepository.existsById("p-1")).thenReturn(false);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        service.onPaymentSuccessful(successful("p-1"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaidAt()).isEqualTo(PAID_AT);
        assertThat(order.getPaymentReference()).isEqualTo("pay-ref-1");
        assertThat(order.paymentState()).isEqualTo(PaymentState.PAID);
        verify(orderRepository).save(order);

        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo("p-1");
        assertThat(captor.getValue().getEventType()).isEqualTo(PaymentSuccessfulEvent.TYPE);
    }

    @Test
    void successful_duplicate_isSkipped() {
        when(processedEventRepository.existsById("p-1")).thenReturn(true);

        service.onPaymentSuccessful(successful("p-1"));

        verifyNoInteractions(orderRepository);
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void successful_afterAdminCancel_recordsPaymentButKeepsCancelled() {
        Order order = order(OrderStatus.CANCELLED);
        when(processedEventRepository.existsById("p-2")).thenReturn(false);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        service.onPaymentSuccessful(successful("p-2"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaidAt()).isEqualTo(PAID_AT);
        assertThat(order.paymentState()).isEqualTo(PaymentState.PAID); // admin app flags the refund
    }

    @Test
    void successful_afterShipmentEvent_keepsShipped() {
        Order order = order(OrderStatus.SHIPPED);
        when(processedEventRepository.existsById("p-3")).thenReturn(false);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        service.onPaymentSuccessful(successful("p-3"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.getPaymentReference()).isEqualTo("pay-ref-1");
    }

    @Test
    void failed_movesPendingOrderToPaymentFailed_andTruncatesTheReason() {
        Order order = order(OrderStatus.PENDING_PAYMENT);
        when(processedEventRepository.existsById("f-1")).thenReturn(false);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        service.onPaymentFailed(failed("f-1", "x".repeat(400)));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(order.getPaymentFailedAt()).isEqualTo(PAID_AT);
        assertThat(order.getPaymentFailureReason()).hasSize(OrderPaymentService.FAILURE_REASON_MAX_LENGTH);
        assertThat(order.paymentState()).isEqualTo(PaymentState.FAILED);
        verify(orderRepository).save(order);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void failed_afterSuccessfulPayment_isIgnored() {
        Order order = order(OrderStatus.PAID);
        order.setPaidAt(PAID_AT);
        order.setPaymentReference("pay-ref-1");
        when(processedEventRepository.existsById("f-2")).thenReturn(false);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        service.onPaymentFailed(failed("f-2", "Card declined"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaymentFailedAt()).isNull();
        assertThat(order.getPaymentReference()).isEqualTo("pay-ref-1");
        verify(orderRepository, never()).save(any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void unknownOrder_isRecordedAsProcessed_withoutSaving() {
        when(processedEventRepository.existsById("p-9")).thenReturn(false);
        when(orderRepository.findById(42L)).thenReturn(Optional.empty());

        service.onPaymentSuccessful(successful("p-9"));

        verify(orderRepository, never()).save(any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void eventWithoutId_isIgnored() {
        service.onPaymentFailed(failed(null, "Card declined"));

        verifyNoInteractions(orderRepository, processedEventRepository);
    }

    @Test
    void paymentState_isDerivedForOrdersWithoutPaymentDetails() {
        assertThat(order(OrderStatus.PENDING_PAYMENT).paymentState()).isEqualTo(PaymentState.PENDING);
        assertThat(order(OrderStatus.DELIVERED).paymentState()).isEqualTo(PaymentState.PAID);
        assertThat(order(OrderStatus.PAYMENT_FAILED).paymentState()).isEqualTo(PaymentState.FAILED);
        assertThat(order(OrderStatus.CANCELLED).paymentState()).isEqualTo(PaymentState.PENDING);
    }
}
