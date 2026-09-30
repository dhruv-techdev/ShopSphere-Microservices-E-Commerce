package com.shopsphere.orderservice.entity;

public enum OrderStatus {
    /** Order created, awaiting payment. */
    PENDING_PAYMENT,
    /** Payment captured successfully. */
    PAID,
    /** Payment failed; order kept for audit. */
    PAYMENT_FAILED,
    /** Items shipped to the customer. */
    SHIPPED,
    /** Order completed. */
    DELIVERED,
    /** Cancelled by customer or system. */
    CANCELLED;

    /**
     * Forward-only state machine.
     *
     * <p>PENDING_PAYMENT may jump straight to SHIPPED/DELIVERED because order-service does
     * not consume payment events yet — a shipment only exists after payment.successful, so
     * a shipment event is itself proof of payment. Terminal states never move.</p>
     */
    public boolean canTransitionTo(OrderStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case PENDING_PAYMENT -> target != PENDING_PAYMENT;
            case PAID -> target == SHIPPED || target == DELIVERED || target == CANCELLED;
            case SHIPPED -> target == DELIVERED;
            case PAYMENT_FAILED, DELIVERED, CANCELLED -> false;
        };
    }
}
