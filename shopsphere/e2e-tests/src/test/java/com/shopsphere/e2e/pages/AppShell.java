package com.shopsphere.e2e.pages;

import com.shopsphere.e2e.support.Ui;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/** Toolbar + side navigation around every signed-in page. */
public final class AppShell extends PageObject {

    private static final By USER_MENU = Ui.testId("user-menu");
    private static final By SIGN_OUT = Ui.testId("logout");

    private AppShell(WebDriver driver) {
        super(driver);
    }

    public static AppShell at(WebDriver driver) {
        AppShell shell = new AppShell(driver);
        shell.ui.visible(USER_MENU);
        return shell;
    }

    /** Toolbar button text, e.g. {@code account_circle admin@shopsphere.test} (icon ligature included). */
    public String userMenuText() {
        return ui.text(USER_MENU);
    }

    public ProductListPage goToProducts() {
        ui.click(navLink("/products"));
        return ProductListPage.at(driver);
    }

    public OrderListPage goToOrders() {
        ui.click(navLink("/orders"));
        return OrderListPage.at(driver);
    }

    public LoginPage signOut() {
        ui.click(USER_MENU);
        ui.click(SIGN_OUT);
        return LoginPage.at(driver);
    }

    private static By navLink(String path) {
        return By.cssSelector("mat-nav-list a[href='" + path + "']");
    }
}
