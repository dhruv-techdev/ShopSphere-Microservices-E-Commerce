package com.shopsphere.e2e.pages;

public enum ShipmentStatus implements Labelled {
    PENDING("Ready to ship"),
    SHIPPED("In transit"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled");

    private final String label;

    ShipmentStatus(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }

    public static ShipmentStatus fromLabel(String label) {
        return Labelled.fromLabel(ShipmentStatus.class, label);
    }
}
