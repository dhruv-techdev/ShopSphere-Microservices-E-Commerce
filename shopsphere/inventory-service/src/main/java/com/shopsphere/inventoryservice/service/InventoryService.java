package com.shopsphere.inventoryservice.service;

import com.shopsphere.inventoryservice.dto.CheckAvailabilityRequest;
import com.shopsphere.inventoryservice.dto.CheckAvailabilityResponse;
import com.shopsphere.inventoryservice.dto.InitInventoryRequest;
import com.shopsphere.inventoryservice.dto.StockResponse;
import com.shopsphere.inventoryservice.dto.StockUpdateRequest;
import com.shopsphere.inventoryservice.entity.Inventory;
import com.shopsphere.inventoryservice.exception.InsufficientStockException;
import com.shopsphere.inventoryservice.exception.InventoryAlreadyExistsException;
import com.shopsphere.inventoryservice.exception.InventoryNotFoundException;
import com.shopsphere.inventoryservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Value("${app.inventory.low-stock-threshold}")
    private int lowStockThreshold;

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
    /* Helpers                                                           */
    /* ---------------------------------------------------------------- */

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
