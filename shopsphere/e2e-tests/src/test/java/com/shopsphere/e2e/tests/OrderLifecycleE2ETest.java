package com.shopsphere.e2e.tests;

import com.shopsphere.e2e.fixtures.TestData.SeededOrder;
import com.shopsphere.e2e.model.Amounts;
import com.shopsphere.e2e.pages.OrderDetailPage;
import com.shopsphere.e2e.pages.OrderItem;
import com.shopsphere.e2e.pages.OrderListPage;
import com.shopsphere.e2e.pages.OrderRow;
import com.shopsphere.e2e.pages.OrderStatus;
import com.shopsphere.e2e.pages.PaymentState;
import com.shopsphere.e2e.pages.ShipmentStatus;
import com.shopsphere.e2e.pages.components.ConfirmDialog;
import com.shopsphere.e2e.pages.components.ShipDialog;
import com.shopsphere.e2e.support.Unique;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * PENDING_PAYMENT → PAID (payment-service over Kafka) → SHIPPED (admin dispatch) → DELIVERED,
 * plus cancelling a paid order before dispatch. Requires docker-compose.e2e.yml (payments always succeed).
 */
public class OrderLifecycleE2ETest extends BaseE2ETest {

    @Test(description = "A paid order is shipped with a tracking number and then marked delivered")
    public void paidOrderIsShippedAndDelivered() {
        SeededOrder order = DATA.placeOrder();
        String total = Amounts.display(order.total());
        String tracking = "E2E-" + Unique.token().toUpperCase(Locale.ROOT);

        OrderListPage orders = signInAsAdmin().goToOrders().filterByCustomer(order.customerId());
        assertEquals(orders.awaitRow(order.id()).total(), total);

        OrderDetailPage detail = orders.openOrder(order.id())
                .awaitStatus(OrderStatus.PAID)
                .awaitShipmentStatus(ShipmentStatus.PENDING);
        assertEquals(detail.paymentState(), PaymentState.PAID);
        assertEquals(detail.items(), List.of(new OrderItem(order.productName(), order.quantity(), total)));
        assertEquals(detail.total(), total);
        assertTrue(detail.canShip());
        assertFalse(detail.canDeliver());

        ShipDialog ship = detail.ship();
        assertEquals(ship.title(), "Ship order #" + order.id());
        assertEquals(ship.trackingNumber("no").confirmExpectingError().fieldError(),
                "Use 4–100 letters, digits or dashes");

        detail = ship.carrier("E2E Express").trackingNumber(tracking).confirm()
                .awaitStatus(OrderStatus.SHIPPED)
                .awaitShipmentStatus(ShipmentStatus.SHIPPED);
        assertEquals(detail.trackingNumber(), Optional.of(tracking));
        assertFalse(detail.canShip());
        assertFalse(detail.canCancel(), "goods on the way can't be cancelled");

        ConfirmDialog<OrderDetailPage> deliver = detail.deliver();
        assertEquals(deliver.title(), "Mark as delivered?");
        detail = deliver.confirm()
                .awaitStatus(OrderStatus.DELIVERED)
                .awaitShipmentStatus(ShipmentStatus.DELIVERED);
        assertFalse(detail.canDeliver());

        OrderRow row = OrderListPage.open(driver).filterByCustomer(order.customerId()).awaitRow(order.id());
        assertEquals(row.status(), OrderStatus.DELIVERED);
        assertEquals(row.payment(), PaymentState.PAID);
        assertEquals(row.trackingNumber(), Optional.of(tracking));
    }

    @Test(description = "Cancelling a paid order before dispatch cancels its shipment and flags a manual refund")
    public void paidOrderCancelledBeforeDispatchNeedsRefund() {
        SeededOrder order = DATA.placeOrder();

        OrderDetailPage detail = signInAsAdmin().goToOrders()
                .jumpTo(order.id())
                .awaitStatus(OrderStatus.PAID)
                .awaitShipmentStatus(ShipmentStatus.PENDING);

        ConfirmDialog<OrderDetailPage> cancel = detail.cancel();
        assertEquals(cancel.title(), "Cancel order #" + order.id() + "?");
        assertTrue(cancel.isDestructive());
        assertTrue(cancel.message().contains("refund"), cancel.message());

        detail = cancel.confirm()
                .awaitStatus(OrderStatus.CANCELLED)
                .awaitShipmentStatus(ShipmentStatus.CANCELLED);
        assertTrue(detail.isRefundFlagged());
        assertFalse(detail.canShip());
        assertFalse(detail.canCancel());
    }
}
