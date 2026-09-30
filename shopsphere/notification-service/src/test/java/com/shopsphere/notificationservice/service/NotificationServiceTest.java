package com.shopsphere.notificationservice.service;

import com.shopsphere.common.events.LowStockEvent;
import com.shopsphere.common.events.OrderCancelledEvent;
import com.shopsphere.common.events.OrderCreatedEvent;
import com.shopsphere.common.events.ShipmentDispatchedEvent;
import com.shopsphere.notificationservice.delivery.NotificationDispatcher;
import com.shopsphere.notificationservice.delivery.NotificationDraft;
import com.shopsphere.notificationservice.entity.NotificationType;
import com.shopsphere.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock NotificationDispatcher dispatcher;

    @InjectMocks NotificationService notificationService;

    private NotificationDraft captured() {
        ArgumentCaptor<NotificationDraft> captor = ArgumentCaptor.forClass(NotificationDraft.class);
        verify(dispatcher).deliver(captor.capture());
        return captor.getValue();
    }

    @Test
    void orderCreated_buildsOrderPlacedDraft() {
        notificationService.handleOrderCreated(OrderCreatedEvent.builder()
                .eventId("evt-2").orderId(7L).userId(1L)
                .totalAmount(new BigDecimal("99.99")).itemCount(2)
                .items(List.of()).occurredAt(Instant.now())
                .build());

        NotificationDraft draft = captured();
        assertThat(draft.type()).isEqualTo(NotificationType.ORDER_PLACED);
        assertThat(draft.userId()).isEqualTo(1L);
        assertThat(draft.sourceEventId()).isEqualTo("evt-2");
        assertThat(draft.subject()).contains("#7");
        assertThat(draft.model()).containsEntry("totalAmount", new BigDecimal("99.99")).containsEntry("itemCount", 2);
    }

    @Test
    void orderCancelled_carriesReasonIntoTextAndModel() {
        notificationService.handleOrderCancelled(OrderCancelledEvent.builder()
                .eventId("oc-1").orderId(42L).userId(7L)
                .reason("RESERVATION_EXPIRED")
                .reasonDescription("Payment wasn't confirmed in time.")
                .cancelledAt(Instant.parse("2026-09-26T22:15:00Z"))
                .totalAmount(new BigDecimal("100.00"))
                .build());

        NotificationDraft draft = captured();
        assertThat(draft.type()).isEqualTo(NotificationType.ORDER_CANCELLED);
        assertThat(draft.subject()).isEqualTo("Order #42 has been cancelled");
        assertThat(draft.body()).contains("Payment wasn't confirmed in time.").contains("Sep 26, 2026");
        assertThat(draft.model()).containsEntry("reasonDescription", "Payment wasn't confirmed in time.");
    }

    @Test
    void shipmentDispatched_includesTrackingAndDestination() {
        notificationService.handleShipmentDispatched(ShipmentDispatchedEvent.builder()
                .eventId("ship-1").orderId(42L).userId(7L)
                .carrier("ShopSphere Express").trackingNumber("SSX2609264K7QZ9M2PA")
                .shippedAt(Instant.parse("2026-09-26T12:00:00Z"))
                .shippingAddress(OrderCreatedEvent.ShippingAddress.builder().city("Toronto").country("CA").build())
                .build());

        NotificationDraft draft = captured();
        assertThat(draft.type()).isEqualTo(NotificationType.SHIPMENT_DISPATCHED);
        assertThat(draft.model())
                .containsEntry("trackingNumber", "SSX2609264K7QZ9M2PA")
                .containsEntry("destination", "Toronto, CA")
                .containsEntry("shippedAt", "Sep 26, 2026 at 12:00 UTC");
    }

    @Test
    void lowStock_isAddressedToAdmin() {
        notificationService.handleLowStock(LowStockEvent.builder()
                .eventId("ls-1").productId(10L)
                .sellableQuantity(2).threshold(10).availableQuantity(5).reservedQuantity(3)
                .build());

        NotificationDraft draft = captured();
        assertThat(draft.type()).isEqualTo(NotificationType.LOW_STOCK_ALERT);
        assertThat(draft.userId()).isNull();
        assertThat(draft.model()).containsEntry("productId", 10L);
    }
}
