package com.shopsphere.e2e.support;

import org.openqa.selenium.By;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.Platform;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Explicit-wait primitives shared by all page objects.
 * Every interaction waits for its target and retries on staleness / overlays (Material ripples, closing dialogs).
 */
public final class Ui {

    private static final Duration POLL = Duration.ofMillis(200);
    private static final By PROGRESS = By.cssSelector("mat-progress-bar");

    private final WebDriver driver;
    private final Duration timeout;

    public Ui(WebDriver driver, Duration timeout) {
        this.driver = driver;
        this.timeout = timeout;
    }

    /* ------------------------------ locators ------------------------------ */

    public static By testId(String id) {
        return By.cssSelector("[data-testid=\"" + cssString(id) + "\"]");
    }

    public static By ariaLabel(String tag, String label) {
        return By.cssSelector(tag + "[aria-label=\"" + cssString(label) + "\"]");
    }

    private static String cssString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /* ------------------------------ waiting ------------------------------ */

    public <T> T await(String description, Function<WebDriver, T> condition) {
        return await(timeout, POLL, description, condition);
    }

    /** Waits until {@code condition} returns non-null / non-false. Element lookups that race the DOM are retried. */
    public <T> T await(Duration within, Duration polling, String description, Function<WebDriver, T> condition) {
        return new FluentWait<>(driver)
                .withTimeout(within)
                .pollingEvery(polling)
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class)
                .withMessage(() -> "waiting for " + description + " (url: " + driver.getCurrentUrl() + ")")
                .until(condition);
    }

    public WebElement visible(By locator) {
        return await(locator + " to be visible", ExpectedConditions.visibilityOfElementLocated(locator));
    }

    public void gone(By locator) {
        await(locator + " to disappear", ExpectedConditions.invisibilityOfElementLocated(locator));
    }

    /** Waits for every Material progress bar on the page to go away. */
    public void awaitIdle() {
        await("the page to finish loading", d -> d.findElements(PROGRESS).isEmpty());
    }

    public void awaitPath(String expectedPath) {
        await("the URL path to be " + expectedPath, d -> expectedPath.equals(path(d)));
    }

    public void awaitPathMatching(String regex) {
        await("the URL path to match " + regex, d -> path(d).matches(regex));
    }

    /* ------------------------------ reading ------------------------------ */

    /** Non-waiting presence check; use for "is this action offered?" assertions after the page settled. */
    public boolean isDisplayed(By locator) {
        return driver.findElements(locator).stream().anyMatch(Ui::displayed);
    }

    public String text(By locator) {
        return visible(locator).getText().trim();
    }

    public Optional<String> textIfPresent(By locator) {
        return driver.findElements(locator).stream()
                .filter(Ui::displayed)
                .findFirst()
                .map(element -> element.getText().trim());
    }

    public List<String> texts(By locator) {
        return driver.findElements(locator).stream()
                .filter(Ui::displayed)
                .map(element -> element.getText().trim())
                .toList();
    }

    public String value(By locator) {
        return valueOf(visible(locator));
    }

    public static String path(WebDriver driver) {
        return URI.create(driver.getCurrentUrl()).getPath();
    }

    public static Optional<String> queryParam(WebDriver driver, String name) {
        String query = URI.create(driver.getCurrentUrl()).getRawQuery();
        if (query == null) {
            return Optional.empty();
        }
        return Arrays.stream(query.split("&"))
                .map(pair -> pair.split("=", 2))
                .filter(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8).equals(name))
                .map(pair -> pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "")
                .findFirst();
    }

    /* ------------------------------ acting ------------------------------ */

    public void click(By locator) {
        await(locator + " to accept a click", d -> {
            WebElement element = d.findElement(locator);
            if (!element.isDisplayed() || !element.isEnabled()) {
                return false;
            }
            try {
                element.click();
                return true;
            } catch (ElementNotInteractableException coveredOrAnimating) {
                return false;
            }
        });
    }

    /**
     * Replaces the field's content with key events so Angular's reactive forms see real input events
     * ({@code WebElement.clear()} does not fire {@code input}).
     * No click first: sendKeys focuses the element itself, while a mouse click on an empty Material
     * field lands on the floating {@code <mat-label>} overlaying it (ElementClickInterceptedException).
     */
    public void type(By locator, String text) {
        WebElement element = visible(locator);
        element.sendKeys(Keys.chord(selectAllModifier(), "a"), Keys.DELETE);
        if (!text.isEmpty()) {
            element.sendKeys(text);
        }
        await(locator + " to contain '" + text + "'", d -> text.equals(valueOf(d.findElement(locator))));
    }

    private static Keys selectAllModifier() {
        return Platform.getCurrent().is(Platform.MAC) ? Keys.COMMAND : Keys.CONTROL;
    }

    private static String valueOf(WebElement element) {
        String value = element.getDomProperty("value");
        return value == null ? "" : value;
    }

    private static boolean displayed(WebElement element) {
        try {
            return element.isDisplayed();
        } catch (StaleElementReferenceException e) {
            return false;
        }
    }
}
