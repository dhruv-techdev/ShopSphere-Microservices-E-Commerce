package com.shopsphere.inventoryservice.exception;

public class InventoryNotFoundException extends RuntimeException {
    public InventoryNotFoundException(Long productId) {
        super("No inventory record for product: " + productId);
    }
}
