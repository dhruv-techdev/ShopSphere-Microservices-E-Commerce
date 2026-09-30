package com.shopsphere.inventoryservice.service;

import com.shopsphere.common.events.OrderCreatedEvent;
import com.shopsphere.common.events.OrderItemSnapshot;
import com.shopsphere.common.events.PaymentFailedEvent;
import com.shopsphere.common.events.PaymentSuccessfulEvent;
import com.shopsphere.inventoryservice.dto.StockUpdateRequest;
import com.shopsphere.inventoryservice.entity.Inventory;
import com.shopsphere.inventoryservice.entity.ReservationStatus;
import com.shopsphere.inventoryservice.entity.StockReservation;
import com.shopsphere.inventoryservice.exception.InsufficientStockException;
import com.shopsphere.inventoryservice.exception.InventoryNotFoundException;
import com.shopsphere.inventoryservice.repository.InventoryRepository;
import com.shopsphere.inventoryservice.repository.StockReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    @Mock InventoryRepository inventoryRepository;
    @Mock StockReservationRepository reservationRepository;

    InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(inventoryRepository, reservationRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(inventoryService, "lowStockThreshold", 10);
        ReflectionTestUtils.setField(inventoryService, "reservationTtl", Duration.ofMinutes(15));
    }

    private static Inventory inventory(int available, int reserved) {
        return Inventory.builder().id(1L).productId(1L)
                .availableQuantity(available).reservedQuantity(reserved).build();
    }

    private static StockReservation hold(ReservationStatus status, int quantity) {
        return StockReservation.builder().id(5L).orderId(42L).productId(1L)
                .quantity(quantity).status(status)
                .reservationExpiresAt(NOW.minusSeconds(60)).build();
    }

    /* ---------------------------- stock updates ---------------------------- */

    @Test
    void updateStock_notFound_throws() {
        when(inventoryRepository.findByProductIdForUpdate(99L)).thenReturn(Optional.empty());

        StockUpdateRequest req = new StockUpdateRequest(StockUpdateRequest.Operation.SET, 5);

        assertThatThrownBy(() -> inventoryService.updateStock(99L, req))
                .isInstanceOf(InventoryNotFoundException.class);
    }

    @Test
    void updateStock_decrementBelowReserved_throws() {
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory(10, 8)));

        StockUpdateRequest req = new StockUpdateRequest(StockUpdateRequest.Operation.DECREMENT, 5);

        assertThatThrownBy(() -> inventoryService.updateStock(1L, req))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void updateStock_increment_succeeds() {
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory(10, 0)));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(i -> i.getArgument(0));

        StockUpdateRequest req = new StockUpdateRequest(StockUpdateRequest.Operation.INCREMENT, 5);
        var response = inventoryService.updateStock(1L, req);

        assertThat(response.getAvailableQuantity()).isEqualTo(15);
    }

    /* ------------------------------ reserve ------------------------------ */

    private static OrderCreatedEvent orderCreated(int quantity) {
        return OrderCreatedEvent.builder()
                .eventId("oc-1").orderId(42L).userId(7L).itemCount(quantity)
                .items(List.of(OrderCreatedEvent.Item.builder().productId(1L).quantity(quantity).build()))
                .build();
    }

    @Test
    void reserve_createsHoldWithExpiry_andIncreasesReserved() {
        Inventory inv = inventory(10, 0);
        when(reservationRepository.findByOrderIdAndProductId(42L, 1L)).thenReturn(Optional.empty());
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inv));

        inventoryService.reserveForOrder(orderCreated(3));

        assertThat(inv.getReservedQuantity()).isEqualTo(3);
        ArgumentCaptor<StockReservation> captor = ArgumentCaptor.forClass(StockReservation.class);
        verify(reservationRepository).save(captor.capture());
        StockReservation saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(saved.getQuantity()).isEqualTo(3);
        assertThat(saved.getReservationExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void reserve_redeliveredEvent_doesNotReserveTwice() {
        when(reservationRepository.findByOrderIdAndProductId(42L, 1L))
                .thenReturn(Optional.of(hold(ReservationStatus.RESERVED, 3)));

        inventoryService.reserveForOrder(orderCreated(3));

        verify(inventoryRepository, never()).findByProductIdForUpdate(any());
        verify(reservationRepository, never()).save(any());
    }

    /* ------------------------------ commit ------------------------------ */

    private static PaymentSuccessfulEvent paymentSuccessful(int quantity) {
        return PaymentSuccessfulEvent.builder()
                .eventId("ps-1").orderId(42L).userId(7L)
                .items(List.of(OrderItemSnapshot.builder().productId(1L).quantity(quantity).build()))
                .build();
    }

    @Test
    void commit_reservedHold_deductsAvailableAndReserved() {
        Inventory inv = inventory(10, 3);
        StockReservation hold = hold(ReservationStatus.RESERVED, 3);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inv));
        when(reservationRepository.findByOrderIdAndProductId(42L, 1L)).thenReturn(Optional.of(hold));

        inventoryService.commitReservation(paymentSuccessful(3));

        assertThat(inv.getAvailableQuantity()).isEqualTo(7);
        assertThat(inv.getReservedQuantity()).isZero();
        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.COMMITTED);
    }

    @Test
    void commit_afterExpiry_takesFromSellableStockOnly() {
        Inventory inv = inventory(10, 2);   // 2 held by some other order
        StockReservation hold = hold(ReservationStatus.EXPIRED, 3);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inv));
        when(reservationRepository.findByOrderIdAndProductId(42L, 1L)).thenReturn(Optional.of(hold));

        inventoryService.commitReservation(paymentSuccessful(3));

        assertThat(inv.getAvailableQuantity()).isEqualTo(7);
        assertThat(inv.getReservedQuantity()).isEqualTo(2); // other order's hold untouched
        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.COMMITTED);
    }

    @Test
    void commit_afterExpiry_withoutStock_doesNotGoNegative() {
        Inventory inv = inventory(5, 5);    // nothing sellable
        StockReservation hold = hold(ReservationStatus.EXPIRED, 3);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inv));
        when(reservationRepository.findByOrderIdAndProductId(42L, 1L)).thenReturn(Optional.of(hold));

        inventoryService.commitReservation(paymentSuccessful(3));

        assertThat(inv.getAvailableQuantity()).isEqualTo(5);
        assertThat(inv.getReservedQuantity()).isEqualTo(5);
        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.COMMITTED);
    }

    /* ------------------------------ release ------------------------------ */

    @Test
    void release_afterExpiry_doesNotStealOtherOrdersHolds() {
        Inventory inv = inventory(10, 2);   // 2 held by another order
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inv));
        when(reservationRepository.findByOrderIdAndProductId(42L, 1L))
                .thenReturn(Optional.of(hold(ReservationStatus.EXPIRED, 3)));

        inventoryService.releaseReservation(PaymentFailedEvent.builder()
                .eventId("pf-1").orderId(42L).userId(7L)
                .items(List.of(OrderItemSnapshot.builder().productId(1L).quantity(3).build()))
                .build());

        assertThat(inv.getReservedQuantity()).isEqualTo(2);
        verify(inventoryRepository, never()).save(any());
    }
}
