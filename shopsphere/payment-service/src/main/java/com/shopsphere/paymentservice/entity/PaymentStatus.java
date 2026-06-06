package com.shopsphere.paymentservice.entity;

public enum PaymentStatus {
    /** Recorded but not yet processed. Brief window before SUCCESSFUL/FAILED. */
    PENDING,
    /** Payment captured. Triggers stock deduction in US19. */
    SUCCESSFUL,
    /** Payment refused (simulated). Triggers stock release + order cancel in US19. */
    FAILED,
    /** Reversed after capture. For future use; not produced by the simulator. */
    REFUNDED
}
