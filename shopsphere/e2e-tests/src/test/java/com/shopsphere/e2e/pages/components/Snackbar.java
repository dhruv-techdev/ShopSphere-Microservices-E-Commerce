package com.shopsphere.e2e.pages.components;

import com.shopsphere.e2e.pages.PageObject;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/** Material snackbar used by NotifierService for success/error toasts. */
public final class Snackbar extends PageObject {

    private static final By MESSAGE = By.cssSelector("mat-snack-bar-container .mat-mdc-snack-bar-label");

    private Snackbar(WebDriver driver) {
        super(driver);
    }

    public static Snackbar of(WebDriver driver) {
        return new Snackbar(driver);
    }

    /** Fails unless a toast with exactly this text shows up (toasts auto-dismiss after 3–6 s). */
    public void expect(String message) {
        ui.await("a snackbar saying '" + message + "'", d -> ui.texts(MESSAGE).contains(message));
    }
}
