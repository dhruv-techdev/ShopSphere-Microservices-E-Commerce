package com.shopsphere.e2e.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/** One row of the products table, as displayed. */
public record ProductRow(String name, String category, String price, String stock, String status) {

    static final By NAME = By.cssSelector("td.mat-column-name a.cell-link");

    static ProductRow from(WebElement row) {
        return new ProductRow(
                row.findElement(NAME).getText().trim(),
                cell(row, "category"),
                cell(row, "price"),
                cell(row, "stockQuantity"),
                cell(row, "active"));
    }

    private static String cell(WebElement row, String column) {
        return row.findElement(By.cssSelector("td.mat-column-" + column)).getText().trim();
    }
}
