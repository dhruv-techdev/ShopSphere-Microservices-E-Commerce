package com.shopsphere.e2e.pages;

public enum PaymentState implements Labelled {
    PENDING("Pending"),
    PAID("Paid"),
    FAILED("Failed");

    private final String label;

    PaymentState(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }

    public static PaymentState fromLabel(String label) {
        return Labelled.fromLabel(PaymentState.class, label);
    }
}
