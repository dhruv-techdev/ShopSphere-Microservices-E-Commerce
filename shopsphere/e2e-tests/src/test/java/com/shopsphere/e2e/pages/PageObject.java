package com.shopsphere.e2e.pages;

import com.shopsphere.e2e.config.E2eConfig;
import com.shopsphere.e2e.support.Ui;
import org.openqa.selenium.WebDriver;

/** Base for pages and reusable components (dialogs, snackbars). */
public abstract class PageObject {

    protected final WebDriver driver;
    protected final E2eConfig config;
    protected final Ui ui;

    protected PageObject(WebDriver driver) {
        this.driver = driver;
        this.config = E2eConfig.get();
        this.ui = new Ui(driver, config.uiTimeout());
    }
}
