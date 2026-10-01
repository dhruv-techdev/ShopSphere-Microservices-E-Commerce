package com.shopsphere.e2e.support;

import com.shopsphere.e2e.config.E2eConfig;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogType;

import java.time.Duration;
import java.util.Map;

/** One fresh Chrome per test method: the admin session lives in sessionStorage, so tests never share state. */
public final class DriverFactory {

    private DriverFactory() {
    }

    public static WebDriver create(E2eConfig config) {
        ChromeOptions options = new ChromeOptions();
        if (config.headless()) {
            options.addArguments("--headless=new");
        }
        options.addArguments(
                "--window-size=1440,960",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--lang=en-US",
                "--disable-search-engine-choice-screen");
        config.chromeBinary().ifPresent(binary -> options.setBinary(binary.toFile()));
        options.setCapability("goog:loggingPrefs", Map.of(LogType.BROWSER, "ALL"));

        WebDriver driver = new ChromeDriver(options);
        // Explicit waits only: an implicit wait would silently slow down every "is it absent?" check.
        driver.manage().timeouts()
                .implicitlyWait(Duration.ZERO)
                .pageLoadTimeout(Duration.ofSeconds(30))
                .scriptTimeout(Duration.ofSeconds(30));
        return driver;
    }
}
