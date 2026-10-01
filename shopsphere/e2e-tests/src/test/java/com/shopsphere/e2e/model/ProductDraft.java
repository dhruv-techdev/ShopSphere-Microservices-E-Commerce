package com.shopsphere.e2e.model;

import com.shopsphere.e2e.support.Unique;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** What an admin types into the product form (and what the API seeds). Price is normalised to cents. */
public record ProductDraft(String name, String description, BigDecimal price, int stock, boolean active) {

    public ProductDraft {
        Objects.requireNonNull(name, "name");
        description = description == null ? "" : description;
        price = Objects.requireNonNull(price, "price").setScale(2, RoundingMode.HALF_UP);
    }

    /** e.g. {@code E2E Desk Lamp mg3k2x9q4-03}, active, 24.50, 12 in stock. */
    public static ProductDraft unique(String label) {
        return new ProductDraft("E2E " + label + " " + Unique.token(),
                "Created by the ShopSphere Selenium suite", new BigDecimal("24.50"), 12, true);
    }

    public ProductDraft withName(String newName) {
        return new ProductDraft(newName, description, price, stock, active);
    }

    public ProductDraft withDescription(String newDescription) {
        return new ProductDraft(name, newDescription, price, stock, active);
    }

    public ProductDraft withPrice(String newPrice) {
        return new ProductDraft(name, description, new BigDecimal(newPrice), stock, active);
    }

    public ProductDraft withStock(int newStock) {
        return new ProductDraft(name, description, price, newStock, active);
    }

    public ProductDraft withActive(boolean newActive) {
        return new ProductDraft(name, description, price, stock, newActive);
    }
}
