package com.shopsphere.e2e.tests;

import com.shopsphere.e2e.model.Amounts;
import com.shopsphere.e2e.model.ProductDraft;
import com.shopsphere.e2e.pages.ProductFormPage;
import com.shopsphere.e2e.pages.ProductListPage;
import com.shopsphere.e2e.pages.ProductRow;
import com.shopsphere.e2e.pages.components.ConfirmDialog;
import com.shopsphere.e2e.pages.components.Snackbar;
import com.shopsphere.e2e.support.Ui;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ProductCrudE2ETest extends BaseE2ETest {

    private static final String NO_MATCHES = "No products match these filters.";

    @Test(description = "Create: a new product is saved, listed and loads back unchanged")
    public void createProduct() {
        ProductDraft draft = ProductDraft.unique("Desk Lamp").withPrice("1249.90").withStock(25);

        ProductFormPage form = signInAsAdmin().goToProducts().createProduct();
        assertEquals(form.heading(), "New product");
        assertEquals(form.saveButtonLabel(), "Create product");

        ProductListPage list = form.fill(draft).save();
        Snackbar.of(driver).expect("Created \"" + draft.name() + "\".");

        ProductRow row = list.search(draft.name()).row(draft.name());
        assertEquals(row.price(), Amounts.display(draft.price()));
        assertEquals(row.stock(), "25");
        assertEquals(row.status(), "Active");

        assertEquals(list.edit(draft.name()).values(), draft);
    }

    @Test(description = "Create: required fields are enforced client-side")
    public void requiredFieldsAreValidated() {
        ProductFormPage form = signInAsAdmin().goToProducts().createProduct().saveExpectingErrors();

        assertTrue(form.fieldErrors().containsAll(List.of("Name is required", "Price is required")),
                "field errors: " + form.fieldErrors());
        assertEquals(Ui.path(driver), "/products/new");
    }

    @Test(description = "Update: name, price, stock and status changes are persisted")
    public void editProduct() {
        ProductDraft original = ProductDraft.unique("Notebook");
        DATA.createProduct(original);
        ProductDraft updated = original
                .withName(original.name() + " v2")
                .withDescription("Updated by the Selenium suite")
                .withPrice("12.75")
                .withStock(3)
                .withActive(false);

        ProductFormPage form = signInAsAdmin().goToProducts().search(original.name()).edit(original.name());
        assertEquals(form.heading(), original.name());
        assertEquals(form.saveButtonLabel(), "Save changes");
        assertEquals(form.values(), original);

        ProductListPage list = form.fill(updated).save();
        Snackbar.of(driver).expect("Saved \"" + updated.name() + "\".");

        ProductRow row = list.search(updated.name()).row(updated.name());
        assertEquals(row.price(), "12.75");
        assertEquals(row.stock(), "3");
        assertEquals(row.status(), "Inactive");
        assertFalse(list.search(original.name()).isListed(original.name()), "old name is gone");
    }

    @Test(description = "Delete from the list: cancelling keeps the product, confirming removes it")
    public void deleteFromList() {
        ProductDraft draft = ProductDraft.unique("Mug");
        DATA.createProduct(draft);
        ProductListPage list = signInAsAdmin().goToProducts().search(draft.name());

        ConfirmDialog<ProductListPage> dialog = list.delete(draft.name());
        assertEquals(dialog.title(), "Delete product?");
        assertTrue(dialog.message().contains(draft.name()));
        assertTrue(dialog.isDestructive());
        assertTrue(dialog.cancel().isListed(draft.name()), "cancel keeps the product");

        list.delete(draft.name()).confirm();
        Snackbar.of(driver).expect("Deleted \"" + draft.name() + "\".");
        assertEquals(list.awaitNotListed(draft.name()).emptyStateMessage(), NO_MATCHES);
    }

    @Test(description = "Delete from the edit form returns to the list without the product")
    public void deleteFromEditForm() {
        ProductDraft draft = ProductDraft.unique("Poster");
        DATA.createProduct(draft);

        ProductListPage list = signInAsAdmin().goToProducts()
                .search(draft.name())
                .edit(draft.name())
                .delete()
                .confirm();
        Snackbar.of(driver).expect("Deleted \"" + draft.name() + "\".");

        assertEquals(list.search(draft.name()).emptyStateMessage(), NO_MATCHES);
    }
}
