package com.shopsphere.e2e.support;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Screenshot, DOM, URL and browser console of a failed test — uploaded by CI. */
public final class FailureArtifacts {

    private static final System.Logger LOG = System.getLogger(FailureArtifacts.class.getName());

    private FailureArtifacts() {
    }

    /** Best effort: never throws, so the original assertion failure stays the reported cause. */
    public static void capture(WebDriver driver, Path root, String testName) {
        Path dir = root.resolve(testName.replaceAll("[^A-Za-z0-9._-]", "_"));
        try {
            Files.createDirectories(dir);
            if (driver instanceof TakesScreenshot camera) {
                Files.write(dir.resolve("screenshot.png"), camera.getScreenshotAs(OutputType.BYTES));
            }
            Files.writeString(dir.resolve("url.txt"), Objects.requireNonNullElse(driver.getCurrentUrl(), ""));
            Files.writeString(dir.resolve("page.html"), Objects.requireNonNullElse(driver.getPageSource(), ""));
            Files.write(dir.resolve("browser-console.log"), consoleLines(driver));
            LOG.log(System.Logger.Level.INFO, "Failure artifacts for {0}: {1}", testName, dir);
        } catch (IOException | WebDriverException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not capture failure artifacts for " + testName, e);
        }
    }

    private static List<String> consoleLines(WebDriver driver) {
        try {
            return driver.manage().logs().get(LogType.BROWSER).getAll().stream()
                    .map(LogEntry::toString)
                    .toList();
        } catch (WebDriverException | UnsupportedOperationException e) {
            return List.of("Browser console unavailable: " + e.getMessage());
        }
    }
}
