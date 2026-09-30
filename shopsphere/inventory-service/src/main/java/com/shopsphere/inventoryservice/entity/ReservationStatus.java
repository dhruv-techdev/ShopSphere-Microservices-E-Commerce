package com.shopsphere.inventoryservice.entity;

public enum ReservationStatus {
    /** Units held against an unpaid order; counted in Inventory.reservedQuantity. */
    RESERVED,
    /** Payment succeeded; units deducted from available stock. */
    COMMITTED,
    /** Payment failed; hold returned to sellable stock. */
    RELEASED,
    /** Hold timed out before payment settled; returned to sellable stock by the sweeper. */
    EXPIRED
}
