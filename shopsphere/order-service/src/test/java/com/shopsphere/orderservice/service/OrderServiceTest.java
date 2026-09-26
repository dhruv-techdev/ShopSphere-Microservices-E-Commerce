package com.shopsphere.orderservice.service;

import com.shopsphere.common.events.OrderCreatedEvent;
import com.shopsphere.orderservice.client.CartClient;
import com.shopsphere.orderservice.client.CartDto;
import com.shopsphere.orderservice.client.CartItemDto;
import com.shopsphere.orderservice.client.InventoryClient;
import com.shopsphere.orderservice.client.ProductClient;
import com.shopsphere.orderservice.client.ProductDto;
import com.shopsphere.orderservice.dto.AddressDto;
import com.shopsphere.orderservice.dto.CreateOrderRequest;
import com.shopsphere.orderservice.dto.OrderResponse;
import com.shopsphere.orderservice.entity.Address;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.exception.InsufficientStockException;
import com.shopsphere.orderservice.exception.OrderAccessDeniedException;
import com.shopsphere.orderservice.exception.OrderNotFoundException;
import com.shopsphere.orderservice.messaging.OrderEventPublisher;
import com.shopsphere.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    private static AddressDto validAddress() {
        return AddressDto.builder()
                .recipientName("  Jane Doe ")
                .phone("+1 416 555 0199")
                .line1("123 King St W")
                .line2("   ")              // blank optional field -> stored as null
                .city("Toronto")
                .state("ON")
                .postalCode("m5v 3l9")     // normalised to upper case
                .country("ca")             // normalised to upper case
                .build();
    }

    private static CreateOrderRequest requestWithAddress() {
        return CreateOrderRequest.builder().shippingAddress(validAddress()).build();
    }

    @Test
    void createOrder_happyPath_persistsShippingAddressAndPublishesItInEvent() {
        CartDto cart = new CartDto(1L,
                List.of(new CartItemDto(10L, "Keyboard", new BigDecimal("50.00"), 2)),
                new BigDecimal("100.00"), 2);
        when(cartClient.getCart(1L)).thenReturn(cart);

        var ia = new InventoryClient.ItemAvailability(10L, 2, 5, true, null);
        when(inventoryClient.checkAvailability(any()))
                .thenReturn(new InventoryClient.AvailabilityResult(true, List.of(ia)));

        when(productClient.fetchProduct(10L))
                .thenReturn(new ProductDto(10L, "Keyboard", new BigDecimal("50.00"), 5, true));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(42L);
            return o;
        });

        OrderResponse response = orderService.createOrder(1L, requestWithAddress());

        // Persisted entity carries the normalised address
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Address saved = orderCaptor.getValue().getShippingAddress();
        assertThat(saved).isNotNull();
        assertThat(saved.getRecipientName()).isEqualTo("Jane Doe");
        assertThat(saved.getLine2()).isNull();
        assertThat(saved.getPostalCode()).isEqualTo("M5V 3L9");
        assertThat(saved.getCountry()).isEqualTo("CA");
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        // order.created payload carries the same address
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        OrderCreatedEvent event = ((OrderService.OrderPersisted) eventCaptor.getValue()).event();
        assertThat(event.getOrderId()).isEqualTo(42L);
        assertThat(event.getShippingAddress()).isNotNull();
        assertThat(event.getShippingAddress().getRecipientName()).isEqualTo("Jane Doe");
        assertThat(event.getShippingAddress().getLine1()).isEqualTo("123 King St W");
        assertThat(event.getShippingAddress().getCity()).isEqualTo("Toronto");
        assertThat(event.getShippingAddress().getPostalCode()).isEqualTo("M5V 3L9");
        assertThat(event.getShippingAddress().getCountry()).isEqualTo("CA");

        // API response exposes it too
        assertThat(response.getShippingAddress()).isNotNull();
        assertThat(response.getShippingAddress().getCity()).isEqualTo("Toronto");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("100.00");

        verify(cartClient).clearCart(1L);
    }

    @Test
    void createOrder_missingShippingAddress_failsFastWithoutRemoteCalls() {
        assertThatThrownBy(() -> orderService.createOrder(1L, new CreateOrderRequest()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("shippingAddress");

        verifyNoInteractions(cartClient, inventoryClient, productClient, orderRepository);
    }

    @Test
    void createOrder_stockShort_throwsAndDoesNotPersist() {
        CartDto cart = new CartDto(1L,
                List.of(new CartItemDto(1L, "P", new BigDecimal("10.00"), 100)),
                new BigDecimal("1000.00"), 100);
        when(cartClient.getCart(1L)).thenReturn(cart);

        var ia = new InventoryClient.ItemAvailability(1L, 100, 5, false, "INSUFFICIENT_STOCK");
        var result = new InventoryClient.AvailabilityResult(false, List.of(ia));
        when(inventoryClient.checkAvailability(any())).thenReturn(result);

        assertThatThrownBy(() -> orderService.createOrder(1L, requestWithAddress()))
                .isInstanceOf(InsufficientStockException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void getOrderById_returnsShippingAddress() {
        Order order = Order.builder()
                .id(1L).userId(1L).status(OrderStatus.PAID)
                .totalAmount(new BigDecimal("10.00")).itemCount(1)
                .shippingAddress(validAddress().toEntity())
                .build();
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderById(1L, 1L);

        assertThat(response.getShippingAddress().getCountry()).isEqualTo("CA");
    }

    @Test
    void getOrderById_legacyOrderWithoutAddress_returnsNullAddress() {
        Order order = Order.builder()
                .id(2L).userId(1L).status(OrderStatus.PAID)
                .totalAmount(new BigDecimal("10.00")).itemCount(1)
                .build();
        when(orderRepository.findWithItemsById(2L)).thenReturn(Optional.of(order));

        assertThat(orderService.getOrderById(1L, 2L).getShippingAddress()).isNull();
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
