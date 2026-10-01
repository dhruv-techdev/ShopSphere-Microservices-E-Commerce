package com.shopsphere.e2e.pages;

import com.shopsphere.e2e.pages.components.ConfirmDialog;
import com.shopsphere.e2e.support.Ui;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** /products — searchable catalog table. */
public final class ProductListPage extends AdminPage {

    private static final By NEW_PRODUCT = Ui.testId("new-product");
    private static final By SEARCH = Ui.testId("search");
    private static final By ROW = Ui.testId("product-row");
    private static final By EMPTY_STATE = By.cssSelector("td.empty");
    private static final By LOADING = By.cssSelector(".table-card mat-progress-bar");

    private ProductListPage(WebDriver driver) {
        super(driver);
    }

    public static ProductListPage open(WebDriver driver) {
        ProductListPage page = new ProductListPage(driver);
        driver.get(page.config.url("/products"));
        return page.awaitLoaded();
    }

    public static ProductListPage at(WebDriver driver) {
        return new ProductListPage(driver).awaitLoaded();
    }

    /** Types into the debounced search box and waits until the table shows that result set. */
    public ProductListPage search(String term) {
        String expected = term.trim();
        String needle = expected.toLowerCase(Locale.ROOT);
        ui.type(SEARCH, term);
        ui.await("the product list to be filtered by '" + expected + "'", d ->
                Ui.queryParam(d, "q").filter(expected::equals).isPresent()
                        && !ui.isDisplayed(LOADING)
                        && (ui.isDisplayed(EMPTY_STATE)
                            || names().stream().allMatch(name -> name.toLowerCase(Locale.ROOT).contains(needle))));
        return this;
    }

    public List<String> names() {
        return driver.findElements(ROW).stream().map(ProductListPage::nameOf).toList();
    }

    public boolean isListed(String name) {
        return find(name).isPresent();
    }

    public ProductRow row(String name) {
        return ui.await("product '" + name + "' to be listed", d -> find(name).orElse(null));
    }

    public ProductListPage awaitNotListed(String name) {
        ui.await("product '" + name + "' to leave the list", d -> find(name).isEmpty());
        return this;
    }

    public String emptyStateMessage() {
        return ui.text(EMPTY_STATE);
    }

    public ProductFormPage createProduct() {
        ui.click(NEW_PRODUCT);
        return ProductFormPage.awaitCreate(driver);
    }

    public ProductFormPage edit(String name) {
        ui.click(Ui.ariaLabel("a", "Edit " + name));
        return ProductFormPage.awaitEdit(driver);
    }

    public ConfirmDialog<ProductListPage> delete(String name) {
        ui.click(Ui.ariaLabel("button", "Delete " + name));
        return ConfirmDialog.await(driver, () -> this);
    }

    private Optional<ProductRow> find(String name) {
        return driver.findElements(ROW).stream()
                .filter(row -> nameOf(row).equals(name))
                .findFirst()
                .map(ProductRow::from);
    }

    private static String nameOf(WebElement row) {
        return row.findElement(ProductRow.NAME).getText().trim();
    }

    private ProductListPage awaitLoaded() {
        ui.awaitPath("/products");
        ui.visible(NEW_PRODUCT);
        ui.await("the product table to load", d -> !ui.isDisplayed(LOADING)
                && (ui.isDisplayed(ROW) || ui.isDisplayed(EMPTY_STATE)));
        return this;
    }
}
