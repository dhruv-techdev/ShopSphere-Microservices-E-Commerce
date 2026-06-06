package com.shopsphere.orderservice.service;

import com.shopsphere.common.events.OrderCreatedEvent;
import com.shopsphere.orderservice.client.CartClient;
import com.shopsphere.orderservice.client.CartDto;
import com.shopsphere.orderservice.client.CartItemDto;
import com.shopsphere.orderservice.client.InventoryClient;
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
import com.shopsphere.orderservice.messaging.OrderEventPublisher;
import com.shopsphere.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartClient cartClient;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final OrderEventPublisher orderEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;

    /* ---------------------------------------------------------------- */
    /* Create order                                                      */
    /* ---------------------------------------------------------------- */

    @Transactional
    public OrderResponse createOrder(Long userId, CreateOrderRequest request) {
        // 1. Fetch cart
        CartDto cart = cartClient.getCart(userId);
        log.debug("Fetched cart for user {} with {} items", userId, cart.getItems().size());

        // 2. Stock check via inventory-service
        List<InventoryClient.Item> stockItems = cart.getItems().stream()
                .map(ci -> new InventoryClient.Item(ci.getProductId(), ci.getQuantity()))
                .toList();
        InventoryClient.AvailabilityResult availability = inventoryClient.checkAvailability(stockItems);

        if (!availability.allAvailable()) {
            InventoryClient.ItemAvailability firstShort = availability.items().stream()
                    .filter(i -> !i.available())
                    .findFirst()
                    .orElseThrow();
            log.warn("Order rejected for user {} — product {} short by {}",
                    userId, firstShort.productId(),
                    firstShort.requestedQuantity() - firstShort.sellableQuantity());
            throw new InsufficientStockException(
                    "product " + firstShort.productId(),
                    firstShort.requestedQuantity(),
                    firstShort.sellableQuantity());
        }

        // 3. Fetch products for price + active-flag snapshot
        Map<Long, ProductDto> productById = new HashMap<>();
        for (CartItemDto cartItem : cart.getItems()) {
            ProductDto product = productClient.fetchProduct(cartItem.getProductId());
            if (Boolean.FALSE.equals(product.getActive())) {
                throw new ProductUnavailableException(
                        "Product '" + product.getName() + "' is no longer available");
            }
            productById.put(product.getId(), product);
        }

        // 4. Build the order
        Order order = Order.builder()
                .userId(userId)
                .status(OrderStatus.PENDING_PAYMENT)
                .build();

        BigDecimal computedTotal = BigDecimal.ZERO;
        int computedItemCount = 0;

        for (CartItemDto cartItem : cart.getItems()) {
            ProductDto product = productById.get(cartItem.getProductId());

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

        // 5. Build the event and ask Spring to publish it AFTER COMMIT
        OrderCreatedEvent event = buildOrderCreatedEvent(saved);
        applicationEventPublisher.publishEvent(new OrderPersisted(event));

        // 6. Clear cart (best-effort)
        try {
            cartClient.clearCart(userId);
        } catch (Exception ex) {
            log.warn("Order {} created but cart clear failed for user {}: {}",
                    saved.getId(), userId, ex.getMessage());
        }

        return toResponse(saved);
    }

    /**
     * Spring fires this only after the DB transaction commits successfully.
     * If the transaction rolls back, the event is never published — correct behavior:
     * downstream services shouldn't react to an order that doesn't exist.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPersisted(OrderPersisted persisted) {
        orderEventPublisher.publishOrderCreated(persisted.event());
    }

    /** Internal Spring event wrapper. */
    private record OrderPersisted(OrderCreatedEvent event) {}

    private OrderCreatedEvent buildOrderCreatedEvent(Order order) {
        List<OrderCreatedEvent.Item> items = order.getItems().stream()
                .map(oi -> OrderCreatedEvent.Item.builder()
                        .productId(oi.getProductId())
                        .productName(oi.getProductName())
                        .unitPrice(oi.getUnitPrice())
                        .quantity(oi.getQuantity())
                        .build())
                .toList();

        return OrderCreatedEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .eventType(OrderCreatedEvent.TYPE)
                .occurredAt(java.time.Instant.now())
                .orderId(order.getId())
                .userId(order.getUserId())
                .totalAmount(order.getTotalAmount())
                .itemCount(order.getItemCount())
                .items(items)
                .build();
    }

    /* ---------------------------------------------------------------- */
    /* Read APIs (unchanged from US14)                                   */
    /* ---------------------------------------------------------------- */

    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getMyOrders(Long userId, OrderStatus status, Pageable pageable) {
        Page<Order> page = (status == null)
                ? orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageable);
        return page.map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long userId, Long orderId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.getUserId().equals(userId)) {
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
