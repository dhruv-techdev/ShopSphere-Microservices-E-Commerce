package com.shopsphere.orderservice.service;

import com.shopsphere.common.events.OrderCancelledEvent;
import com.shopsphere.orderservice.dto.OrderResponse;
import com.shopsphere.orderservice.entity.CancellationReason;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderItem;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.exception.OrderNotFoundException;
import com.shopsphere.orderservice.exception.OrderStatusConflictException;
import com.shopsphere.orderservice.repository.OrderRepository;
import com.shopsphere.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock OrderService orderService;
    @Mock ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks AdminOrderService adminOrderService;

    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(1L, "admin@shopsphere.test", "ADMIN");

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

    @Test
    void cancel_pendingOrder_cancelsWithAdminReason_andQueuesOrderCancelled() {
        Order order = order(OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findWithItemsById(42L)).thenReturn(Optional.of(order));
        when(orderService.toResponse(order)).thenReturn(OrderResponse.builder().id(42L).status(OrderStatus.CANCELLED).build());

        OrderResponse response = adminOrderService.cancel(42L, ADMIN);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo(CancellationReason.ADMIN_CANCELLED);
        assertThat(order.getCancelledAt()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        OrderCancelledEvent event = ((OrderCancellationService.OrderCancellationCommitted) captor.getValue()).event();
        assertThat(event.getReason()).isEqualTo("ADMIN_CANCELLED");
        assertThat(event.getUserId()).isEqualTo(7L);
        assertThat(event.getItems()).hasSize(1);
    }

    @Test
    void cancel_paidOrder_isAllowed() {
        Order order = order(OrderStatus.PAID);
        when(orderRepository.findWithItemsById(42L)).thenReturn(Optional.of(order));

        adminOrderService.cancel(42L, ADMIN);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancel_shippedOrder_isConflict() {
        when(orderRepository.findWithItemsById(42L)).thenReturn(Optional.of(order(OrderStatus.SHIPPED)));

        assertThatThrownBy(() -> adminOrderService.cancel(42L, ADMIN))
                .isInstanceOf(OrderStatusConflictException.class)
                .hasMessageContaining("SHIPPED");

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(applicationEventPublisher);
    }

    @Test
    void cancel_alreadyCancelled_isIdempotent() {
        Order order = order(OrderStatus.CANCELLED);
        when(orderRepository.findWithItemsById(42L)).thenReturn(Optional.of(order));

        adminOrderService.cancel(42L, ADMIN);

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(applicationEventPublisher);
    }

    @Test
    void cancel_unknownOrder_is404() {
        when(orderRepository.findWithItemsById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminOrderService.cancel(99L, ADMIN)).isInstanceOf(OrderNotFoundException.class);
    }
}
