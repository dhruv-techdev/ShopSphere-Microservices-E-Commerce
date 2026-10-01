package com.shopsphere.e2e.api;

/** order-service AddressDto. */
public record ShippingAddress(
        String recipientName,
        String phone,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String country) {

    public static ShippingAddress toronto(String recipientName) {
        return new ShippingAddress(recipientName, "+1 416 555 0199", "123 King St W", null,
                "Toronto", "ON", "M5V 3L9", "CA");
    }
}
