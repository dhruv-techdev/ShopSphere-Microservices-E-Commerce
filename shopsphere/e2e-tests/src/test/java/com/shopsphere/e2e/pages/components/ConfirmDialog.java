package com.shopsphere.e2e.pages.components;

import com.shopsphere.e2e.pages.PageObject;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.util.function.Supplier;

/**
 * The shared confirm dialog (core/ui/confirm-dialog.component.ts).
 *
 * @param <T> page shown once the dialog closes
 */
public final class ConfirmDialog<T> extends PageObject {

    private static final By CONTAINER = By.cssSelector("mat-dialog-container");
    private static final By TITLE = By.cssSelector("mat-dialog-container .mat-mdc-dialog-title");
    private static final By MESSAGE = By.cssSelector("mat-dialog-container .mat-mdc-dialog-content p");
    private static final By CONFIRM = By.cssSelector("mat-dialog-container [data-testid='confirm']");
    private static final By CANCEL = By.cssSelector(
            "mat-dialog-container .mat-mdc-dialog-actions button:not([data-testid='confirm'])");

    private final Supplier<T> afterClose;

    private ConfirmDialog(WebDriver driver, Supplier<T> afterClose) {
        super(driver);
        this.afterClose = afterClose;
    }

    public static <T> ConfirmDialog<T> await(WebDriver driver, Supplier<T> afterClose) {
        ConfirmDialog<T> dialog = new ConfirmDialog<>(driver, afterClose);
        dialog.ui.visible(CONFIRM);
        return dialog;
    }

    public String title() {
        return ui.text(TITLE);
    }

    public String message() {
        return ui.text(MESSAGE);
    }

    public String confirmLabel() {
        return ui.text(CONFIRM);
    }

    /** Destructive actions render the confirm button in the danger style. */
    public boolean isDestructive() {
        String classes = ui.visible(CONFIRM).getDomAttribute("class");
        return classes != null && classes.contains("danger-button");
    }

    public T confirm() {
        return close(CONFIRM);
    }

    public T cancel() {
        return close(CANCEL);
    }

    private T close(By button) {
        ui.click(button);
        ui.gone(CONTAINER);
        return afterClose.get();
    }
}
