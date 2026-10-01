package com.shopsphere.e2e.pages;

import com.shopsphere.e2e.support.Ui;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.Optional;

/** /orders — every customer's orders, filterable by status and customer. */
public final class OrderListPage extends AdminPage {

    private static final By JUMP = Ui.testId("jump");
    private static final By JUMP_OPEN = By.cssSelector("form.jump button[type='submit']");
    private static final By CUSTOMER_FILTER = Ui.testId("user-filter");
    private static final By ROW = Ui.testId("order-row");
    private static final By EMPTY_STATE = By.cssSelector("td.empty");
    private static final By LOADING = By.cssSelector(".table-card mat-progress-bar");

    private OrderListPage(WebDriver driver) {
        super(driver);
    }

    public static OrderListPage open(WebDriver driver) {
        OrderListPage page = new OrderListPage(driver);
        driver.get(page.config.url("/orders"));
        return page.awaitLoaded();
    }

    public static OrderListPage at(WebDriver driver) {
        return new OrderListPage(driver).awaitLoaded();
    }

    public OrderListPage filterByCustomer(long customerId) {
        String expected = Long.toString(customerId);
        ui.type(CUSTOMER_FILTER, expected);
        ui.await("the order list to be filtered by customer #" + customerId, d ->
                Ui.queryParam(d, "userId").filter(expected::equals).isPresent() && !ui.isDisplayed(LOADING));
        return this;
    }

    public OrderRow awaitRow(long orderId) {
        return ui.await("order #" + orderId + " to be listed", d -> rowElement(orderId).map(OrderRow::from).orElse(null));
    }

    public OrderDetailPage openOrder(long orderId) {
        ui.await("order #" + orderId + " to be openable", d -> rowElement(orderId)
                .map(row -> {
                    row.findElement(OrderRow.LINK).click();
                    return true;
                })
                .orElse(false));
        return OrderDetailPage.at(driver, orderId);
    }

    /** The "Order #" quick-open box in the header. */
    public OrderDetailPage jumpTo(long orderId) {
        ui.type(JUMP, Long.toString(orderId));
        ui.click(JUMP_OPEN);
        return OrderDetailPage.at(driver, orderId);
    }

    private Optional<WebElement> rowElement(long orderId) {
        return driver.findElements(ROW).stream()
                .filter(row -> OrderRow.idOf(row) == orderId)
                .findFirst();
    }

    private OrderListPage awaitLoaded() {
        ui.awaitPath("/orders");
        ui.visible(JUMP);
        ui.await("the order table to load", d -> !ui.isDisplayed(LOADING)
                && (ui.isDisplayed(ROW) || ui.isDisplayed(EMPTY_STATE)));
        return this;
    }
}
