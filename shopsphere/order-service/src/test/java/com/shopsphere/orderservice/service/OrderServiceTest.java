package com.shopsphere.orderservice.service;

import com.shopsphere.orderservice.client.CartClient;
import com.shopsphere.orderservice.client.CartDto;
import com.shopsphere.orderservice.client.CartItemDto;
import com.shopsphere.orderservice.client.InventoryClient;
import com.shopsphere.orderservice.client.ProductClient;
import com.shopsphere.orderservice.dto.CreateOrderRequest;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.exception.InsufficientStockException;
import com.shopsphere.orderservice.exception.OrderAccessDeniedException;
import com.shopsphere.orderservice.exception.OrderNotFoundException;
import com.shopsphere.orderservice.messaging.OrderEventPublisher;
import com.shopsphere.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock CartClient cartClient;
    @Mock ProductClient productClient;
    @Mock InventoryClient inventoryClient;
    @Mock OrderEventPublisher orderEventPublisher;
    @Mock ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks OrderService orderService;

    @Test
    void createOrder_stockShort_throwsAndDoesNotPersist() {
        CartDto cart = new CartDto(1L,
                List.of(new CartItemDto(1L, "P", new BigDecimal("10.00"), 100)),
                new BigDecimal("1000.00"), 100);
        when(cartClient.getCart(1L)).thenReturn(cart);

        var ia = new InventoryClient.ItemAvailability(1L, 100, 5, false, "INSUFFICIENT_STOCK");
        var result = new InventoryClient.AvailabilityResult(false, List.of(ia));
        when(inventoryClient.checkAvailability(any())).thenReturn(result);

        assertThatThrownBy(() -> orderService.createOrder(1L, new CreateOrderRequest()))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void getOrderById_wrongOwner_throwsAccessDenied() {
        Order order = Order.builder().id(1L).userId(99L).build();
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrderById(1L, 1L))
                .isInstanceOf(OrderAccessDeniedException.class);
    }

    @Test
    void getOrderById_notFound_throws() {
        when(orderRepository.findWithItemsById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(1L, 99L))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
