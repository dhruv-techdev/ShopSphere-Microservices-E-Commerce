package com.shopsphere.inventoryservice.service;

import com.shopsphere.common.events.OrderItemSnapshot;
import com.shopsphere.common.events.PaymentFailedEvent;
import com.shopsphere.common.events.PaymentSuccessfulEvent;
import com.shopsphere.common.events.ReservationExpiredEvent;
import com.shopsphere.inventoryservice.config.ClockConfig;
import com.shopsphere.inventoryservice.entity.Inventory;
import com.shopsphere.inventoryservice.entity.ReservationStatus;
import com.shopsphere.inventoryservice.entity.StockReservation;
import com.shopsphere.inventoryservice.messaging.InventoryEventPublisher;
import com.shopsphere.inventoryservice.repository.InventoryRepository;
import com.shopsphere.inventoryservice.repository.StockReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * US37 — reservation expiry against a real database, so @Version checks actually fire.
 *
 * Seed for every test (product 1: available 10, reserved 5):
 *   order 42 → 3 units, hold EXPIRED 1 minute ago  (sweeper target)
 *   order 44 → 2 units, hold still valid for 10 min (must never be touched)
 */
@DataJpaTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:inventory-expiry;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS shopsphere_inventory",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED) // tests drive their own commits
@Import({ReservationExpiryService.class, ReservationExpirySweeper.class, InventoryService.class, ClockConfig.class})
class ReservationExpiryOptimisticLockingTest {

    private static final long PRODUCT = 1L;
    private static final long EXPIRED_ORDER = 42L;
    private static final long ACTIVE_ORDER = 44L;

    @Autowired InventoryRepository inventoryRepository;
    @Autowired StockReservationRepository reservationRepository;
    @Autowired ReservationExpiryService expiryService;
    @Autowired ReservationExpirySweeper sweeper;
    @Autowired InventoryService inventoryService;
    @Autowired PlatformTransactionManager transactionManager;

    @MockBean InventoryEventPublisher eventPublisher;

    private TransactionTemplate tx;
    private TransactionTemplate concurrentTx;

    @BeforeEach
    void seed() {
        tx = new TransactionTemplate(transactionManager);
        concurrentTx = new TransactionTemplate(transactionManager);
        concurrentTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        tx.executeWithoutResult(s -> {
            // Bulk deletes run immediately; deleteAll() would be flushed after the inserts below
            // (Hibernate orders INSERTs before DELETEs) and trip uk_inventory_product.
            reservationRepository.deleteAllInBatch();
            inventoryRepository.deleteAllInBatch();

            inventoryRepository.save(Inventory.builder()
                    .productId(PRODUCT).availableQuantity(10).reservedQuantity(5).build());

            Instant now = Instant.now();
            reservationRepository.save(StockReservation.builder()
                    .orderId(EXPIRED_ORDER).productId(PRODUCT).quantity(3)
                    .status(ReservationStatus.RESERVED)
                    .reservationExpiresAt(now.minus(Duration.ofMinutes(1)))
                    .build());
            reservationRepository.save(StockReservation.builder()
                    .orderId(ACTIVE_ORDER).productId(PRODUCT).quantity(2)
                    .status(ReservationStatus.RESERVED)
                    .reservationExpiresAt(now.plus(Duration.ofMinutes(10)))
                    .build());
        });
    }

    private Inventory inventory() {
        return inventoryRepository.findByProductId(PRODUCT).orElseThrow();
    }

    private ReservationStatus statusOf(long orderId) {
        return reservationRepository.findByOrderIdAndProductId(orderId, PRODUCT).orElseThrow().getStatus();
    }

    @Test
    void sweep_releasesOnlyOverdueHolds_andPublishesEvent() {
        int expired = sweeper.sweep();

        assertThat(expired).isEqualTo(1);
        assertThat(statusOf(EXPIRED_ORDER)).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(statusOf(ACTIVE_ORDER)).isEqualTo(ReservationStatus.RESERVED);
        assertThat(inventory().getReservedQuantity()).isEqualTo(2);
        assertThat(inventory().getAvailableQuantity()).isEqualTo(10);

        ArgumentCaptor<ReservationExpiredEvent> captor = ArgumentCaptor.forClass(ReservationExpiredEvent.class);
        verify(eventPublisher).publishReservationExpired(captor.capture());
        ReservationExpiredEvent event = captor.getValue();
        assertThat(event.getOrderId()).isEqualTo(EXPIRED_ORDER);
        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getItems()).extracting(OrderItemSnapshot::getQuantity).containsExactly(3);

        assertThat(sweeper.sweep()).isZero(); // nothing left to do
    }

    @Test
    void concurrentStockUpdate_causesOptimisticLockFailure_expiryRollsBack_andNextSweepSucceeds() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            // Sweeper reads the hold and the inventory row (version N) ...
            List<StockReservation> overdue =
                    reservationRepository.findByOrderIdAndStatus(EXPIRED_ORDER, ReservationStatus.RESERVED);
            Inventory staleView = inventoryRepository.findByProductId(PRODUCT).orElseThrow();
            assertThat(staleView.getAvailableQuantity()).isEqualTo(10);

            // ... meanwhile an admin restock commits first (version N+1) ...
            concurrentTx.executeWithoutResult(s -> {
                Inventory fresh = inventoryRepository.findByProductId(PRODUCT).orElseThrow();
                fresh.setAvailableQuantity(fresh.getAvailableQuantity() + 5);
                inventoryRepository.save(fresh);
            });

            // ... so the sweeper's write against version N must fail.
            expiryService.applyExpiry(EXPIRED_ORDER, overdue, Instant.now());
        })).isInstanceOf(OptimisticLockingFailureException.class);

        // Rolled back: hold still RESERVED, restock kept, no event.
        assertThat(statusOf(EXPIRED_ORDER)).isEqualTo(ReservationStatus.RESERVED);
        assertThat(inventory().getAvailableQuantity()).isEqualTo(15);
        assertThat(inventory().getReservedQuantity()).isEqualTo(5);
        verify(eventPublisher, never()).publishReservationExpired(any());

        // Next pass works from fresh state.
        assertThat(sweeper.sweep()).isEqualTo(1);
        assertThat(statusOf(EXPIRED_ORDER)).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(inventory().getAvailableQuantity()).isEqualTo(15);
        assertThat(inventory().getReservedQuantity()).isEqualTo(2);
        verify(eventPublisher, times(1)).publishReservationExpired(any());
    }

    @Test
    void twoSweepersRacing_onlyOneReleasesTheHold() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            // Sweeper instance A reads the overdue hold (version N) ...
            List<StockReservation> overdue =
                    reservationRepository.findByOrderIdAndStatus(EXPIRED_ORDER, ReservationStatus.RESERVED);

            // ... instance B expires the same order and commits first ...
            concurrentTx.executeWithoutResult(s -> assertThat(expiryService.expireOrder(EXPIRED_ORDER)).isTrue());

            // ... so A's write against version N must fail instead of releasing a second time.
            expiryService.applyExpiry(EXPIRED_ORDER, overdue, Instant.now());
        })).isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(statusOf(EXPIRED_ORDER)).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(statusOf(ACTIVE_ORDER)).isEqualTo(ReservationStatus.RESERVED);
        assertThat(inventory().getReservedQuantity()).isEqualTo(2);   // released once, not twice
        verify(eventPublisher, times(1)).publishReservationExpired(any());
    }

    @Test
    void latePayment_afterExpiry_commitsFromSellableStock() {
        sweeper.sweep();

        inventoryService.commitReservation(PaymentSuccessfulEvent.builder()
                .eventId("ps-late").orderId(EXPIRED_ORDER).userId(7L)
                .items(List.of(OrderItemSnapshot.builder().productId(PRODUCT).quantity(3).build()))
                .build());

        assertThat(statusOf(EXPIRED_ORDER)).isEqualTo(ReservationStatus.COMMITTED);
        assertThat(inventory().getAvailableQuantity()).isEqualTo(7);
        assertThat(inventory().getReservedQuantity()).isEqualTo(2);   // order 44's hold intact
    }

    @Test
    void paymentFailed_afterExpiry_doesNotReleaseTwice() {
        sweeper.sweep();

        inventoryService.releaseReservation(PaymentFailedEvent.builder()
                .eventId("pf-late").orderId(EXPIRED_ORDER).userId(7L)
                .items(List.of(OrderItemSnapshot.builder().productId(PRODUCT).quantity(3).build()))
                .build());

        assertThat(statusOf(EXPIRED_ORDER)).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(inventory().getReservedQuantity()).isEqualTo(2);   // not 0 — order 44 still holds 2
    }
}
