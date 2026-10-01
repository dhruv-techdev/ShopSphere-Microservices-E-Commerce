package com.shopsphere.e2e.pages;

import com.shopsphere.e2e.model.ProductDraft;
import com.shopsphere.e2e.pages.components.ConfirmDialog;
import com.shopsphere.e2e.support.Ui;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.math.BigDecimal;
import java.util.List;

/** /products/new and /products/:id/edit. */
public final class ProductFormPage extends AdminPage {

    private static final By HEADING = By.cssSelector("header.page-header h1");
    private static final By NAME = Ui.testId("name");
    private static final By DESCRIPTION = By.cssSelector("textarea[formcontrolname='description']");
    private static final By PRICE = Ui.testId("price");
    private static final By STOCK = Ui.testId("stock");
    private static final By ACTIVE = By.cssSelector("mat-slide-toggle[formcontrolname='active'] button[role='switch']");
    private static final By SAVE = Ui.testId("save");
    private static final By DELETE = Ui.testId("delete-product");
    private static final By FIELD_ERROR = By.cssSelector("mat-form-field mat-error");
    private static final By LOADING = By.cssSelector(".form-card mat-progress-bar");

    private ProductFormPage(WebDriver driver) {
        super(driver);
    }

    static ProductFormPage awaitCreate(WebDriver driver) {
        ProductFormPage page = new ProductFormPage(driver);
        page.ui.awaitPath("/products/new");
        page.ui.visible(SAVE);
        return page;
    }

    static ProductFormPage awaitEdit(WebDriver driver) {
        ProductFormPage page = new ProductFormPage(driver);
        page.ui.awaitPathMatching("/products/\\d+/edit");
        page.ui.await("the product to load into the form",
                d -> !page.ui.isDisplayed(LOADING) && !page.ui.value(NAME).isEmpty());
        return page;
    }

    public String heading() {
        return ui.text(HEADING);
    }

    public String saveButtonLabel() {
        return ui.text(SAVE);
    }

    public ProductFormPage fill(ProductDraft draft) {
        ui.type(NAME, draft.name());
        ui.type(DESCRIPTION, draft.description());
        ui.type(PRICE, draft.price().toPlainString());
        ui.type(STOCK, Integer.toString(draft.stock()));
        return active(draft.active());
    }

    public ProductFormPage active(boolean active) {
        if (isActive() != active) {
            ui.click(ACTIVE);
            ui.await("the Active toggle to be " + (active ? "on" : "off"), d -> isActive() == active);
        }
        return this;
    }

    /** Values currently in the form, e.g. to verify what the edit screen loaded. */
    public ProductDraft values() {
        return new ProductDraft(
                ui.value(NAME),
                ui.value(DESCRIPTION),
                new BigDecimal(ui.value(PRICE)),
                Integer.parseInt(ui.value(STOCK)),
                isActive());
    }

    /** Submits a valid form; the app navigates back to the list. */
    public ProductListPage save() {
        ui.click(SAVE);
        return ProductListPage.at(driver);
    }

    /** Submits an invalid form; the app stays here and flags the fields. */
    public ProductFormPage saveExpectingErrors() {
        ui.click(SAVE);
        ui.visible(FIELD_ERROR);
        return this;
    }

    public List<String> fieldErrors() {
        return ui.texts(FIELD_ERROR);
    }

    public ConfirmDialog<ProductListPage> delete() {
        ui.click(DELETE);
        return ConfirmDialog.await(driver, () -> ProductListPage.at(driver));
    }

    private boolean isActive() {
        return "true".equals(ui.visible(ACTIVE).getDomAttribute("aria-checked"));
    }
}
