package com.shopsphere.e2e.pages;

import org.openqa.selenium.WebDriver;

/** A page rendered inside the signed-in shell (side nav + toolbar). */
public abstract class AdminPage extends PageObject {

    protected AdminPage(WebDriver driver) {
        super(driver);
    }

    public AppShell shell() {
        return AppShell.at(driver);
    }
}
