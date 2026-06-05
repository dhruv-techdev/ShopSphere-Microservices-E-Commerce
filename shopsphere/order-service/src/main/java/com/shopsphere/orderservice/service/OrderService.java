package com.shopsphere.orderservice.service;

import com.shopsphere.orderservice.client.CartClient;
import com.shopsphere.orderservice.client.CartDto;
import com.shopsphere.orderservice.client.CartItemDto;
import com.shopsphere.orderservice.client.ProductClient;
import com.shopsphere.orderservice.client.ProductDto;
import com.shopsphere.orderservice.dto.CreateOrderRequest;
import com.shopsphere.orderservice.dto.OrderItemResponse;
import com.shopsphere.orderservice.dto.OrderResponse;
import com.shopsphere.orderservice.dto.OrderSummaryResponse;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderItem;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.exception.InsufficientStockException;
import com.shopsphere.orderservice.exception.OrderAccessDeniedException;
import com.shopsphere.orderservice.exception.OrderNotFoundException;
import com.shopsphere.orderservice.exception.ProductUnavailableException;
import com.shopsphere.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartClient cartClient;
    private final ProductClient productClient;

    /* ---------------------------------------------------------------- */
    /* Create order (US13)                                               */
    /* ---------------------------------------------------------------- */

    @Transactional
    public OrderResponse createOrder(Long userId, CreateOrderRequest request) {
        CartDto cart = cartClient.getCart(userId);
        log.debug("Fetched cart for user {} with {} items", userId, cart.getItems().size());

        Order order = Order.builder()
                .userId(userId)
                .status(OrderStatus.PENDING_PAYMENT)
                .build();

        BigDecimal computedTotal = BigDecimal.ZERO;
        int computedItemCount = 0;

        for (CartItemDto cartItem : cart.getItems()) {
            ProductDto product = productClient.fetchProduct(cartItem.getProductId());

            if (Boolean.FALSE.equals(product.getActive())) {
                throw new ProductUnavailableException(
                        "Product '" + product.getName() + "' is no longer available");
            }

            if (product.getStockQuantity() != null
                    && product.getStockQuantity() < cartItem.getQuantity()) {
                throw new InsufficientStockException(
                        product.getName(), cartItem.getQuantity(), product.getStockQuantity());
            }

            BigDecimal unitPrice = product.getPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));

            OrderItem orderItem = OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .unitPrice(unitPrice)
                    .quantity(cartItem.getQuantity())
                    .lineTotal(lineTotal)
                    .build();

            order.addItem(orderItem);

            computedTotal = computedTotal.add(lineTotal);
            computedItemCount += cartItem.getQuantity();
        }

        order.setTotalAmount(computedTotal);
        order.setItemCount(computedItemCount);

        Order saved = orderRepository.save(order);
        log.info("Created order {} for user {} (total={}, items={})",
                saved.getId(), userId, computedTotal, computedItemCount);

        try {
            cartClient.clearCart(userId);
        } catch (Exception ex) {
            log.warn("Order {} created but cart clear failed for user {}: {}",
                    saved.getId(), userId, ex.getMessage());
        }

        return toResponse(saved);
    }

    /* ---------------------------------------------------------------- */
    /* ST1 + ST2 — Order history for a user                              */
    /* ---------------------------------------------------------------- */

    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getMyOrders(Long userId, OrderStatus status, Pageable pageable) {
        Page<Order> page = (status == null)
                ? orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageable);

        return page.map(this::toSummary);
    }

    /* ---------------------------------------------------------------- */
    /* ST3 + ST4 + ST5 — Order details by id, with ownership check       */
    /* ---------------------------------------------------------------- */

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long userId, Long orderId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.getUserId().equals(userId)) {
            // Deliberately the same shape as not-found from an attacker's perspective.
            // We log the real reason, but the public response is "Forbidden", not
            // "Order X belongs to user Y" — that would leak ownership info.
            log.warn("User {} attempted to access order {} owned by user {}",
                    userId, orderId, order.getUserId());
            throw new OrderAccessDeniedException(orderId);
        }

        return toResponse(order);
    }

    /* ---------------------------------------------------------------- */
    /* Mappers                                                           */
    /* ---------------------------------------------------------------- */

    public OrderResponse toResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .itemCount(order.getItemCount())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(order.getItems().stream()
                        .map(item -> OrderItemResponse.builder()
                                .id(item.getId())
                                .productId(item.getProductId())
                                .productName(item.getProductName())
                                .unitPrice(item.getUnitPrice())
                                .quantity(item.getQuantity())
                                .lineTotal(item.getLineTotal())
                                .build())
                        .toList())
                .build();
    }

    public OrderSummaryResponse toSummary(Order order) {
        return OrderSummaryResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .itemCount(order.getItemCount())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
