package com.shopsphere.e2e.pages;

import com.shopsphere.e2e.pages.components.ConfirmDialog;
import com.shopsphere.e2e.pages.components.ShipDialog;
import com.shopsphere.e2e.support.Ui;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * /orders/:id — status, payment, shipment and the admin actions (ship, deliver, cancel).
 * Payment and shipment creation happen asynchronously over Kafka, so the await* methods
 * re-load the order with the page's Refresh button until the expected state shows up.
 */
public final class OrderDetailPage extends AdminPage {

    private static final Duration ASYNC_POLL = Duration.ofSeconds(2);

    private static final By STATUS = Ui.testId("order-status");
    private static final By PAYMENT = Ui.testId("payment-state");
    private static final By SHIPMENT = Ui.testId("shipment-status");
    private static final By TRACKING = Ui.testId("tracking-number");
    private static final By TOTAL = Ui.testId("order-total");
    private static final By ITEM = Ui.testId("item-row");
    private static final By SHIP = Ui.testId("ship");
    private static final By DELIVER = Ui.testId("deliver");
    private static final By CANCEL = Ui.testId("cancel-order");
    private static final By REFUND_NEEDED = Ui.testId("refund-needed");
    private static final By SYNCING = Ui.testId("syncing");
    private static final By REFRESH = Ui.ariaLabel("button", "Refresh");
    private static final By LOADING = By.cssSelector("mat-progress-bar");

    private final long orderId;

    private OrderDetailPage(WebDriver driver, long orderId) {
        super(driver);
        this.orderId = orderId;
    }

    public static OrderDetailPage open(WebDriver driver, long orderId) {
        OrderDetailPage page = new OrderDetailPage(driver, orderId);
        driver.get(page.config.url("/orders/" + orderId));
        return page.awaitLoaded();
    }

    public static OrderDetailPage at(WebDriver driver, long orderId) {
        return new OrderDetailPage(driver, orderId).awaitLoaded();
    }

    /* ------------------------------ state ------------------------------ */

    public OrderStatus status() {
        return OrderStatus.fromLabel(ui.text(STATUS));
    }

    public PaymentState paymentState() {
        return PaymentState.fromLabel(ui.text(PAYMENT));
    }

    public Optional<ShipmentStatus> shipmentStatus() {
        return ui.textIfPresent(SHIPMENT).map(ShipmentStatus::fromLabel);
    }

    public Optional<String> trackingNumber() {
        return ui.textIfPresent(TRACKING);
    }

    public String total() {
        return ui.text(TOTAL);
    }

    public List<OrderItem> items() {
        return driver.findElements(ITEM).stream().map(OrderItem::from).toList();
    }

    public boolean canShip() {
        return ui.isDisplayed(SHIP);
    }

    public boolean canDeliver() {
        return ui.isDisplayed(DELIVER);
    }

    public boolean canCancel() {
        return ui.isDisplayed(CANCEL);
    }

    public boolean isRefundFlagged() {
        return ui.isDisplayed(REFUND_NEEDED);
    }

    /* ------------------------------ waiting on async state ------------------------------ */

    public OrderDetailPage awaitStatus(OrderStatus expected) {
        awaitAsync("order #" + orderId + " to be " + expected, () -> {
            OrderStatus current = status();
            if (current != expected && current.isDeadEnd()) {
                throw new IllegalStateException("Order #" + orderId + " is " + current + " while waiting for "
                        + expected + (current == OrderStatus.PAYMENT_FAILED
                        ? " — run the stack with docker-compose.e2e.yml (PAYMENT_SUCCESS_RATE=1.0)" : ""));
            }
            return current == expected;
        });
        return this;
    }

    public OrderDetailPage awaitShipmentStatus(ShipmentStatus expected) {
        awaitAsync("order #" + orderId + "'s shipment to be " + expected,
                () -> shipmentStatus().filter(expected::equals).isPresent());
        return this;
    }

    private void awaitAsync(String description, BooleanSupplier reached) {
        ui.await(config.asyncTimeout(), ASYNC_POLL, description, d -> {
            if (reached.getAsBoolean()) {
                return true;
            }
            refreshIfIdle();
            return false;
        });
    }

    /** Never interrupts the page's own post-action polling (the "syncing" banner). */
    private void refreshIfIdle() {
        if (ui.isDisplayed(SYNCING) || ui.isDisplayed(LOADING)) {
            return;
        }
        driver.findElements(REFRESH).stream()
                .filter(WebElement::isEnabled)
                .findFirst()
                .ifPresent(WebElement::click);
    }

    /* ------------------------------ actions ------------------------------ */

    public ShipDialog ship() {
        ui.click(SHIP);
        return ShipDialog.await(driver, this);
    }

    public ConfirmDialog<OrderDetailPage> deliver() {
        ui.click(DELIVER);
        return ConfirmDialog.await(driver, this::awaitSettled);
    }

    public ConfirmDialog<OrderDetailPage> cancel() {
        ui.click(CANCEL);
        return ConfirmDialog.await(driver, this::awaitSettled);
    }

    /* ------------------------------ helpers ------------------------------ */

    private OrderDetailPage awaitSettled() {
        ui.await("order #" + orderId + " to finish updating", d -> !ui.isDisplayed(LOADING));
        return this;
    }

    private OrderDetailPage awaitLoaded() {
        ui.awaitPath("/orders/" + orderId);
        ui.visible(STATUS);
        ui.await("order #" + orderId + " to finish loading", d -> !ui.isDisplayed(LOADING));
        return this;
    }
}
