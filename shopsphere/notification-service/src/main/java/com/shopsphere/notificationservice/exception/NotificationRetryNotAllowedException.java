package com.shopsphere.notificationservice.exception;

public class NotificationRetryNotAllowedException extends RuntimeException {
    public NotificationRetryNotAllowedException(String message) {
        super(message);
    }
}
