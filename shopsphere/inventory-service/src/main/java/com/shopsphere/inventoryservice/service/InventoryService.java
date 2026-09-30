package com.shopsphere.inventoryservice.service;

import com.shopsphere.common.events.OrderCreatedEvent;
import com.shopsphere.common.events.OrderItemSnapshot;
import com.shopsphere.common.events.PaymentFailedEvent;
import com.shopsphere.common.events.PaymentSuccessfulEvent;
import com.shopsphere.inventoryservice.dto.CheckAvailabilityRequest;
import com.shopsphere.inventoryservice.dto.CheckAvailabilityResponse;
import com.shopsphere.inventoryservice.dto.InitInventoryRequest;
import com.shopsphere.inventoryservice.dto.StockResponse;
import com.shopsphere.inventoryservice.dto.StockUpdateRequest;
import com.shopsphere.inventoryservice.entity.Inventory;
import com.shopsphere.inventoryservice.entity.ReservationStatus;
import com.shopsphere.inventoryservice.entity.StockReservation;
import com.shopsphere.inventoryservice.exception.InsufficientStockException;
import com.shopsphere.inventoryservice.exception.InventoryAlreadyExistsException;
import com.shopsphere.inventoryservice.exception.InventoryNotFoundException;
import com.shopsphere.inventoryservice.repository.InventoryRepository;
import com.shopsphere.inventoryservice.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StockReservationRepository reservationRepository;
    private final Clock clock;

    @Value("${app.inventory.low-stock-threshold}")
    private int lowStockThreshold;

    /** US37 — how long a hold survives without payment. */
    @Value("${app.inventory.reservation.ttl:15m}")
    private Duration reservationTtl;

    /* ---------------------------------------------------------------- */
    /* Initialization                                                    */
    /* ---------------------------------------------------------------- */

    @Transactional
    public StockResponse initInventory(InitInventoryRequest request) {
        if (inventoryRepository.existsByProductId(request.getProductId())) {
            throw new InventoryAlreadyExistsException(request.getProductId());
        }
        Inventory inv = Inventory.builder()
                .productId(request.getProductId())
                .availableQuantity(request.getInitialQuantity())
                .reservedQuantity(0)
                .build();
        Inventory saved = inventoryRepository.save(inv);
        log.info("Initialised inventory for product {} with quantity {}",
                saved.getProductId(), saved.getAvailableQuantity());
        return toResponse(saved);
    }

    /* ---------------------------------------------------------------- */
    /* Single-product stock check                                        */
    /* ---------------------------------------------------------------- */

    @Transactional(readOnly = true)
    public StockResponse checkStock(Long productId) {
        Inventory inv = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));
        return toResponse(inv);
    }

    @Transactional(readOnly = true)
    public List<StockResponse> findLowStock() {
        return inventoryRepository.findLowStock(lowStockThreshold).stream()
                .map(this::toResponse)
                .toList();
    }

    /* ---------------------------------------------------------------- */
    /* US16 — Batch availability check                                   */
    /* ---------------------------------------------------------------- */

    @Transactional(readOnly = true)
    public CheckAvailabilityResponse checkAvailability(CheckAvailabilityRequest request) {
        // Single DB query for all product IDs — avoids N round-trips
        List<Long> productIds = request.getItems().stream()
                .map(CheckAvailabilityRequest.Item::getProductId)
                .toList();

        Map<Long, Inventory> byProductId = new HashMap<>();
        inventoryRepository.findByProductIdIn(productIds).forEach(inv ->
                byProductId.put(inv.getProductId(), inv));

        boolean allAvailable = true;
        List<CheckAvailabilityResponse.ItemAvailability> results =
                new java.util.ArrayList<>(request.getItems().size());

        for (CheckAvailabilityRequest.Item item : request.getItems()) {
            Inventory inv = byProductId.get(item.getProductId());

            if (inv == null) {
                allAvailable = false;
                results.add(CheckAvailabilityResponse.ItemAvailability.builder()
                        .productId(item.getProductId())
                        .requestedQuantity(item.getQuantity())
                        .sellableQuantity(0)
                        .available(false)
                        .reason("NO_INVENTORY_RECORD")
                        .build());
                continue;
            }

            int sellable = inv.sellable();
            boolean ok = sellable >= item.getQuantity();
            if (!ok) allAvailable = false;

            results.add(CheckAvailabilityResponse.ItemAvailability.builder()
                    .productId(item.getProductId())
                    .requestedQuantity(item.getQuantity())
                    .sellableQuantity(sellable)
                    .available(ok)
                    .reason(ok ? "OK" : "INSUFFICIENT_STOCK")
                    .build());
        }

        return CheckAvailabilityResponse.builder()
                .allAvailable(allAvailable)
                .items(results)
                .build();
    }

    /* ---------------------------------------------------------------- */
    /* Stock update                                                      */
    /* ---------------------------------------------------------------- */

    @Transactional
    public StockResponse updateStock(Long productId, StockUpdateRequest request) {
        Inventory inv = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));

        int before = inv.getAvailableQuantity();
        int after = switch (request.getOperation()) {
            case SET -> request.getQuantity();
            case INCREMENT -> before + request.getQuantity();
            case DECREMENT -> {
                int candidate = before - request.getQuantity();
                if (candidate < inv.getReservedQuantity()) {
                    throw new InsufficientStockException(
                            productId, request.getQuantity(),
                            before - inv.getReservedQuantity());
                }
                yield candidate;
            }
        };

        inv.setAvailableQuantity(after);
        Inventory saved = inventoryRepository.save(inv);
        log.info("Updated inventory for product {} via {}: {} -> {}",
                productId, request.getOperation(), before, after);

        return toResponse(saved);
    }

    /* ---------------------------------------------------------------- */
    /* Kafka event handlers — stock reservation lifecycle (US17 + US37)  */
    /* Products are processed in ascending id order so concurrent        */
    /* transactions take row locks in the same order (no deadlocks).     */
    /* ---------------------------------------------------------------- */

    @Transactional
    public void reserveForOrder(OrderCreatedEvent event) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(reservationTtl);
        log.info("Reserving inventory for order eventId={} orderId={} itemCount={} expiresAt={}",
                event.getEventId(), event.getOrderId(), event.getItemCount(), expiresAt);

        Map<Long, Integer> quantities = quantitiesByProduct(
                event.getItems(), OrderCreatedEvent.Item::getProductId, OrderCreatedEvent.Item::getQuantity);

        for (Map.Entry<Long, Integer> line : quantities.entrySet()) {
            Long productId = line.getKey();
            int quantity = line.getValue();

            // Idempotent: a redelivered order.created must not reserve twice.
            if (reservationRepository.findByOrderIdAndProductId(event.getOrderId(), productId).isPresent()) {
                log.info("Order {} already holds product {} — skipping duplicate reservation",
                        event.getOrderId(), productId);
                continue;
            }

            Inventory inv = lockInventory(productId);
            int newReserved = inv.getReservedQuantity() + quantity;
            if (inv.getAvailableQuantity() < newReserved) {
                throw new InsufficientStockException(productId, quantity, inv.sellable());
            }

            inv.setReservedQuantity(newReserved);
            inventoryRepository.save(inv);
            reservationRepository.save(StockReservation.builder()
                    .orderId(event.getOrderId())
                    .productId(productId)
                    .quantity(quantity)
                    .status(ReservationStatus.RESERVED)
                    .reservationExpiresAt(expiresAt)
                    .build());

            log.debug("Reserved {} units of product {} for order {} until {}",
                    quantity, productId, event.getOrderId(), expiresAt);
        }
    }

    @Transactional
    public void commitReservation(PaymentSuccessfulEvent event) {
        Instant now = clock.instant();
        log.info("Committing reservation for payment eventId={} orderId={}",
                event.getEventId(), event.getOrderId());

        Map<Long, Integer> quantities = quantitiesByProduct(
                event.getItems(), OrderItemSnapshot::getProductId, OrderItemSnapshot::getQuantity);

        for (Map.Entry<Long, Integer> line : quantities.entrySet()) {
            Long productId = line.getKey();
            Inventory inv = lockInventory(productId);
            Optional<StockReservation> maybeHold =
                    reservationRepository.findByOrderIdAndProductId(event.getOrderId(), productId);

            if (maybeHold.isEmpty()) {
                // Hold taken before US37 — only tracked in the aggregate.
                int quantity = line.getValue();
                inv.setAvailableQuantity(inv.getAvailableQuantity() - quantity);
                inv.setReservedQuantity(Math.max(0, inv.getReservedQuantity() - quantity));
                inventoryRepository.save(inv);
                continue;
            }

            StockReservation hold = maybeHold.get();
            int quantity = hold.getQuantity();

            switch (hold.getStatus()) {
                case COMMITTED -> {
                    log.info("Order {} product {} already committed — skipping duplicate", event.getOrderId(), productId);
                    continue;
                }
                case RESERVED -> {
                    inv.setAvailableQuantity(inv.getAvailableQuantity() - quantity);
                    inv.setReservedQuantity(Math.max(0, inv.getReservedQuantity() - quantity));
                }
                case EXPIRED, RELEASED -> {
                    // Payment settled after the hold was released — take it from sellable stock if we still can.
                    if (inv.sellable() >= quantity) {
                        inv.setAvailableQuantity(inv.getAvailableQuantity() - quantity);
                        log.warn("Late payment for order {}: hold on product {} was {}, committed {} from sellable stock",
                                event.getOrderId(), productId, hold.getStatus(), quantity);
                    } else {
                        log.error("OVERSOLD: late payment for order {} needs {} x product {} but only {} sellable — manual follow-up required",
                                event.getOrderId(), quantity, productId, inv.sellable());
                    }
                }
            }

            hold.markCommitted(now);
            inventoryRepository.save(inv);
            reservationRepository.save(hold);
            log.debug("Committed {} units of product {} for order {}", quantity, productId, event.getOrderId());
        }
    }

    @Transactional
    public void releaseReservation(PaymentFailedEvent event) {
        Instant now = clock.instant();
        log.info("Releasing reservation for payment eventId={} orderId={}",
                event.getEventId(), event.getOrderId());

        Map<Long, Integer> quantities = quantitiesByProduct(
                event.getItems(), OrderItemSnapshot::getProductId, OrderItemSnapshot::getQuantity);

        for (Map.Entry<Long, Integer> line : quantities.entrySet()) {
            Long productId = line.getKey();
            Inventory inv = lockInventory(productId);
            Optional<StockReservation> maybeHold =
                    reservationRepository.findByOrderIdAndProductId(event.getOrderId(), productId);

            if (maybeHold.isEmpty()) {
                // Hold taken before US37 — only tracked in the aggregate.
                inv.setReservedQuantity(Math.max(0, inv.getReservedQuantity() - line.getValue()));
                inventoryRepository.save(inv);
                continue;
            }

            StockReservation hold = maybeHold.get();
            if (hold.getStatus() != ReservationStatus.RESERVED) {
                // Already expired/released/committed — releasing again would steal another order's hold.
                log.info("Order {} product {} hold is {} — nothing to release", event.getOrderId(), productId, hold.getStatus());
                continue;
            }

            inv.setReservedQuantity(Math.max(0, inv.getReservedQuantity() - hold.getQuantity()));
            hold.markReleased(now);
            inventoryRepository.save(inv);
            reservationRepository.save(hold);
            log.debug("Released {} units of product {} for order {}", hold.getQuantity(), productId, event.getOrderId());
        }
    }

    /* ---------------------------------------------------------------- */
    /* Helpers                                                           */
    /* ---------------------------------------------------------------- */

    private Inventory lockInventory(Long productId) {
        return inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));
    }

    /** Merges duplicate product lines and sorts by product id (consistent lock order). */
    private static <T> Map<Long, Integer> quantitiesByProduct(List<T> items,
                                                             Function<T, Long> productId,
                                                             Function<T, Integer> quantity) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }
        return items.stream().collect(Collectors.toMap(productId, quantity, Integer::sum, TreeMap::new));
    }

    private StockResponse toResponse(Inventory inv) {
        int sellable = inv.sellable();
        return StockResponse.builder()
                .productId(inv.getProductId())
                .availableQuantity(inv.getAvailableQuantity())
                .reservedQuantity(inv.getReservedQuantity())
                .sellableQuantity(sellable)
                .inStock(sellable > 0)
                .lowStock(sellable <= lowStockThreshold)
                .updatedAt(inv.getUpdatedAt())
                .build();
    }
}
