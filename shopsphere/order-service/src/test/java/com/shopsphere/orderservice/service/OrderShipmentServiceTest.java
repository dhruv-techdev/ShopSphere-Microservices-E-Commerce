package com.shopsphere.orderservice.service;

import com.shopsphere.common.events.ShipmentDeliveredEvent;
import com.shopsphere.common.events.ShipmentDispatchedEvent;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;
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
class OrderShipmentServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock ProcessedEventRepository processedEventRepository;

    @InjectMocks OrderShipmentService service;

    private static final Instant SHIPPED_AT = Instant.parse("2026-09-26T12:00:00Z");
    private static final Instant DELIVERED_AT = Instant.parse("2026-09-28T15:30:00Z");

    private static Order order(OrderStatus status) {
        return Order.builder()
                .id(42L).userId(7L).status(status)
                .totalAmount(new BigDecimal("100.00")).itemCount(2)
                .build();
    }

    private static ShipmentDispatchedEvent dispatched(String eventId) {
        return ShipmentDispatchedEvent.builder()
                .eventId(eventId).eventType(ShipmentDispatchedEvent.TYPE).occurredAt(SHIPPED_AT)
                .shipmentId(5L).orderId(42L).userId(7L)
                .carrier("ShopSphere Express").trackingNumber("SSX2609264K7QZ9M2PA")
                .shippedAt(SHIPPED_AT)
                .build();
    }

    private static ShipmentDeliveredEvent delivered(String eventId) {
        return ShipmentDeliveredEvent.builder()
                .eventId(eventId).eventType(ShipmentDeliveredEvent.TYPE).occurredAt(DELIVERED_AT)
                .shipmentId(5L).orderId(42L).userId(7L)
                .carrier("ShopSphere Express").trackingNumber("SSX2609264K7QZ9M2PA")
                .deliveredAt(DELIVERED_AT)
                .build();
    }

    @Test
    void dispatched_movesPendingOrderToShipped_andStoresTracking() {
        Order order = order(OrderStatus.PENDING_PAYMENT);
        when(processedEventRepository.existsById("d-1")).thenReturn(false);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        service.onShipmentDispatched(dispatched("d-1"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.getShipmentId()).isEqualTo(5L);
        assertThat(order.getCarrier()).isEqualTo("ShopSphere Express");
        assertThat(order.getTrackingNumber()).isEqualTo("SSX2609264K7QZ9M2PA");
        assertThat(order.getShippedAt()).isEqualTo(SHIPPED_AT);
        verify(orderRepository).save(order);

        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo("d-1");
        assertThat(captor.getValue().getEventType()).isEqualTo(ShipmentDispatchedEvent.TYPE);
    }

    @Test
    void dispatched_duplicateEvent_isSkipped() {
        when(processedEventRepository.existsById("d-1")).thenReturn(true);

        service.onShipmentDispatched(dispatched("d-1"));

        verifyNoInteractions(orderRepository);
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void delivered_movesShippedOrderToDelivered() {
        Order order = order(OrderStatus.SHIPPED);
        order.setTrackingNumber("SSX2609264K7QZ9M2PA");
        when(processedEventRepository.existsById("v-1")).thenReturn(false);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        service.onShipmentDelivered(delivered("v-1"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getDeliveredAt()).isEqualTo(DELIVERED_AT);
        verify(orderRepository).save(order);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void dispatched_afterDelivery_doesNotMoveStatusBackwards() {
        Order order = order(OrderStatus.DELIVERED);
        when(processedEventRepository.existsById("d-late")).thenReturn(false);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        service.onShipmentDispatched(dispatched("d-late"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getTrackingNumber()).isEqualTo("SSX2609264K7QZ9M2PA"); // details still filled
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void delivered_onCancelledOrder_isIgnored() {
        Order order = order(OrderStatus.CANCELLED);
        when(processedEventRepository.existsById("v-2")).thenReturn(false);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        service.onShipmentDelivered(delivered("v-2"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void unknownOrder_isRecordedAsProcessed_withoutSaving() {
        when(processedEventRepository.existsById("d-9")).thenReturn(false);
        when(orderRepository.findById(42L)).thenReturn(Optional.empty());

        service.onShipmentDispatched(dispatched("d-9"));

        verify(orderRepository, never()).save(any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void eventWithoutId_isIgnored() {
        service.onShipmentDispatched(dispatched(null));

        verifyNoInteractions(orderRepository, processedEventRepository);
    }
}
