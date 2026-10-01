package com.shopsphere.e2e.pages.components;

import com.shopsphere.e2e.pages.OrderDetailPage;
import com.shopsphere.e2e.pages.PageObject;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/** "Ship order #n" dialog (features/orders/ship-dialog.component.ts). */
public final class ShipDialog extends PageObject {

    private static final By CONTAINER = By.cssSelector("mat-dialog-container");
    private static final By TITLE = By.cssSelector("mat-dialog-container .mat-mdc-dialog-title");
    private static final By CARRIER = By.cssSelector("mat-dialog-container input[formcontrolname='carrier']");
    private static final By TRACKING = By.cssSelector("mat-dialog-container [data-testid='tracking-input']");
    private static final By CONFIRM = By.cssSelector("mat-dialog-container [data-testid='confirm-ship']");
    private static final By FIELD_ERROR = By.cssSelector("mat-dialog-container mat-error");

    private final OrderDetailPage order;

    private ShipDialog(WebDriver driver, OrderDetailPage order) {
        super(driver);
        this.order = order;
    }

    public static ShipDialog await(WebDriver driver, OrderDetailPage order) {
        ShipDialog dialog = new ShipDialog(driver, order);
        dialog.ui.visible(TRACKING);
        return dialog;
    }

    public String title() {
        return ui.text(TITLE);
    }

    public ShipDialog carrier(String carrier) {
        ui.type(CARRIER, carrier);
        return this;
    }

    public ShipDialog trackingNumber(String trackingNumber) {
        ui.type(TRACKING, trackingNumber);
        return this;
    }

    public OrderDetailPage confirm() {
        ui.click(CONFIRM);
        ui.gone(CONTAINER);
        return order;
    }

    /** Submits an invalid form: the dialog stays open and shows the field error. */
    public ShipDialog confirmExpectingError() {
        ui.click(CONFIRM);
        ui.visible(FIELD_ERROR);
        return this;
    }

    public String fieldError() {
        return ui.text(FIELD_ERROR);
    }
}
