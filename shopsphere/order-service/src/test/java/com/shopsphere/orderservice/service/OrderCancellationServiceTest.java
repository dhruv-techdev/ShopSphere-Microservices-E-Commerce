package com.shopsphere.orderservice.service;

import com.shopsphere.common.events.OrderCancelledEvent;
import com.shopsphere.common.events.OrderItemSnapshot;
import com.shopsphere.common.events.ReservationExpiredEvent;
import com.shopsphere.orderservice.entity.CancellationReason;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderItem;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.entity.ProcessedEvent;
import com.shopsphere.orderservice.messaging.OrderEventPublisher;
import com.shopsphere.orderservice.repository.OrderRepository;
import com.shopsphere.orderservice.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCancellationServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock ProcessedEventRepository processedEventRepository;
    @Mock ApplicationEventPublisher applicationEventPublisher;
    @Mock OrderEventPublisher orderEventPublisher;

    @InjectMocks OrderCancellationService service;

    private static Order order(OrderStatus status) {
        Order order = Order.builder()
                .id(42L).userId(7L).status(status)
                .totalAmount(new BigDecimal("100.00")).itemCount(2)
                .build();
        order.addItem(OrderItem.builder()
                .productId(10L).productName("Keyboard")
                .unitPrice(new BigDecimal("50.00")).quantity(2)
                .lineTotal(new BigDecimal("100.00"))
                .build());
        return order;
    }

    private static ReservationExpiredEvent expired(String eventId) {
        return ReservationExpiredEvent.builder()
                .eventId(eventId).eventType(ReservationExpiredEvent.TYPE)
                .orderId(42L).expiredAt(Instant.now())
                .items(List.of(OrderItemSnapshot.builder().productId(10L).quantity(2).build()))
                .build();
    }

    @Test
    void pendingOrder_isCancelledWithReason_andCancellationEventQueuedForAfterCommit() {
        Order order = order(OrderStatus.PENDING_PAYMENT);
        when(processedEventRepository.existsById("rx-1")).thenReturn(false);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        service.onReservationExpired(expired("rx-1"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo(CancellationReason.RESERVATION_EXPIRED);
        assertThat(order.getCancelledAt()).isNotNull();
        verify(orderRepository).save(order);
        verify(processedEventRepository).save(any(ProcessedEvent.class));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        OrderCancelledEvent event = ((OrderCancellationService.OrderCancellationCommitted) captor.getValue()).event();
        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getEventType()).isEqualTo(OrderCancelledEvent.TYPE);
        assertThat(event.getOrderId()).isEqualTo(42L);
        assertThat(event.getUserId()).isEqualTo(7L);
        assertThat(event.getReason()).isEqualTo("RESERVATION_EXPIRED");
        assertThat(event.getReasonDescription()).contains("Payment wasn't confirmed in time");
        assertThat(event.getItems()).extracting(OrderItemSnapshot::getProductId).containsExactly(10L);

        // Kafka publish happens only in the AFTER_COMMIT listener.
        verifyNoInteractions(orderEventPublisher);
        service.onCancellationCommitted((OrderCancellationService.OrderCancellationCommitted) captor.getValue());
        verify(orderEventPublisher).publishOrderCancelled(event);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAID", "SHIPPED", "DELIVERED", "CANCELLED", "PAYMENT_FAILED"})
    void nonPendingOrders_areLeftAlone_butEventIsRecorded(OrderStatus status) {
        Order order = order(status);
        when(processedEventRepository.existsById("rx-2")).thenReturn(false);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        service.onReservationExpired(expired("rx-2"));

        assertThat(order.getStatus()).isEqualTo(status);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(applicationEventPublisher);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void duplicateEvent_isSkipped() {
        when(processedEventRepository.existsById("rx-1")).thenReturn(true);

        service.onReservationExpired(expired("rx-1"));

        verifyNoInteractions(orderRepository, applicationEventPublisher);
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void unknownOrder_isRecordedWithoutCancelling() {
        when(processedEventRepository.existsById("rx-3")).thenReturn(false);
        when(orderRepository.findById(42L)).thenReturn(Optional.empty());

        service.onReservationExpired(expired("rx-3"));

        verifyNoInteractions(applicationEventPublisher);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }
}
