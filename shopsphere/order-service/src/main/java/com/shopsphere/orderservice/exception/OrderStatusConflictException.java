package com.shopsphere.orderservice.exception;

import com.shopsphere.orderservice.entity.OrderStatus;

/** US42 — requested transition isn't allowed from the order's current status. */
public class OrderStatusConflictException extends RuntimeException {
    public OrderStatusConflictException(Long orderId, OrderStatus status, String action) {
        super("Order " + orderId + " is " + status + " and can't be " + action);
    }
}
