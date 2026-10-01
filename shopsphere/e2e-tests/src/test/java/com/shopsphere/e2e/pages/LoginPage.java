package com.shopsphere.e2e.pages;

import com.shopsphere.e2e.api.Credentials;
import com.shopsphere.e2e.support.Ui;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.util.Optional;

public final class LoginPage extends PageObject {

    private static final By EMAIL = By.cssSelector("input[formcontrolname='email']");
    private static final By PASSWORD = By.cssSelector("input[formcontrolname='password']");
    private static final By SUBMIT = Ui.testId("login-submit");
    private static final By ERROR = Ui.testId("login-error");
    private static final By NOTICE = Ui.testId("login-notice");

    private LoginPage(WebDriver driver) {
        super(driver);
    }

    public static LoginPage open(WebDriver driver) {
        LoginPage page = new LoginPage(driver);
        driver.get(page.config.url("/login"));
        return page.awaitLoaded();
    }

    /** After a redirect (guard, sign-out, expired session). */
    public static LoginPage at(WebDriver driver) {
        return new LoginPage(driver).awaitLoaded();
    }

    public AppShell signIn(Credentials credentials) {
        submit(credentials);
        return AppShell.at(driver);
    }

    public LoginPage signInExpectingError(Credentials credentials) {
        submit(credentials);
        ui.visible(ERROR);
        return this;
    }

    public String errorMessage() {
        return ui.text(ERROR);
    }

    public Optional<String> notice() {
        return ui.textIfPresent(NOTICE);
    }

    public String passwordValue() {
        return ui.value(PASSWORD);
    }

    public Optional<String> returnUrl() {
        return Ui.queryParam(driver, "returnUrl");
    }

    private void submit(Credentials credentials) {
        ui.type(EMAIL, credentials.email());
        ui.type(PASSWORD, credentials.password());
        ui.click(SUBMIT);
    }

    private LoginPage awaitLoaded() {
        ui.awaitPath("/login");
        ui.visible(SUBMIT);
        return this;
    }
}
