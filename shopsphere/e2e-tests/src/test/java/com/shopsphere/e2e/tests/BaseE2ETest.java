package com.shopsphere.e2e.tests;

import com.shopsphere.e2e.api.ShopSphereApi;
import com.shopsphere.e2e.config.E2eConfig;
import com.shopsphere.e2e.fixtures.TestData;
import com.shopsphere.e2e.pages.AppShell;
import com.shopsphere.e2e.pages.LoginPage;
import com.shopsphere.e2e.support.DriverFactory;
import com.shopsphere.e2e.support.FailureArtifacts;
import com.shopsphere.e2e.support.StackReadiness;
import org.openqa.selenium.WebDriver;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

/** One browser per test method; data is arranged through the API, behaviour is asserted through the UI. */
public abstract class BaseE2ETest {

    protected static final E2eConfig CONFIG = E2eConfig.get();
    protected static final TestData DATA = new TestData(new ShopSphereApi(CONFIG::api), CONFIG.admin());

    protected WebDriver driver;

    @BeforeSuite(alwaysRun = true)
    public void awaitStack() {
        StackReadiness.await(CONFIG);
    }

    @BeforeMethod(alwaysRun = true)
    public void startBrowser() {
        driver = DriverFactory.create(CONFIG);
    }

    @AfterMethod(alwaysRun = true)
    public void stopBrowser(ITestResult result) {
        if (driver == null) {
            return;
        }
        try {
            if (result.getStatus() == ITestResult.FAILURE) {
                FailureArtifacts.capture(driver, CONFIG.artifactsDir(),
                        getClass().getSimpleName() + "." + result.getMethod().getMethodName());
            }
        } finally {
            driver.quit();
            driver = null;
        }
    }

    protected AppShell signInAsAdmin() {
        return LoginPage.open(driver).signIn(DATA.admin());
    }
}
