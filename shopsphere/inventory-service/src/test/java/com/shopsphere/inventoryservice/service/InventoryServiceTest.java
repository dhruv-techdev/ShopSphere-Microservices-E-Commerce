package com.shopsphere.inventoryservice.service;

import com.shopsphere.inventoryservice.dto.StockUpdateRequest;
import com.shopsphere.inventoryservice.entity.Inventory;
import com.shopsphere.inventoryservice.exception.InsufficientStockException;
import com.shopsphere.inventoryservice.exception.InventoryNotFoundException;
import com.shopsphere.inventoryservice.messaging.InventoryEventPublisher;
import com.shopsphere.inventoryservice.repository.InventoryRepository;
import com.shopsphere.inventoryservice.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock InventoryRepository inventoryRepository;
    @Mock ProcessedEventRepository processedEventRepository;
    @Mock InventoryEventPublisher inventoryEventPublisher;

    @InjectMocks InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(inventoryService, "lowStockThreshold", 10);
    }

    @Test
    void updateStock_notFound_throws() {
        when(inventoryRepository.findByProductIdForUpdate(99L)).thenReturn(Optional.empty());

        StockUpdateRequest req = new StockUpdateRequest(StockUpdateRequest.Operation.SET, 5);

        assertThatThrownBy(() -> inventoryService.updateStock(99L, req))
                .isInstanceOf(InventoryNotFoundException.class);
    }

    @Test
    void updateStock_decrementBelowReserved_throws() {
        Inventory inv = Inventory.builder()
                .id(1L).productId(1L)
                .availableQuantity(10).reservedQuantity(8)
                .build();
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inv));

        StockUpdateRequest req = new StockUpdateRequest(StockUpdateRequest.Operation.DECREMENT, 5);

        assertThatThrownBy(() -> inventoryService.updateStock(1L, req))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void updateStock_increment_succeeds() {
        Inventory inv = Inventory.builder()
                .id(1L).productId(1L)
                .availableQuantity(10).reservedQuantity(0)
                .build();
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inv));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(i -> i.getArgument(0));

        StockUpdateRequest req = new StockUpdateRequest(StockUpdateRequest.Operation.INCREMENT, 5);
        var response = inventoryService.updateStock(1L, req);

        org.assertj.core.api.Assertions.assertThat(response.getAvailableQuantity()).isEqualTo(15);
    }
}
