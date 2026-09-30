package com.shopsphere.userservice.dto;

/** US39 — minimal contact details for service-to-service use (notification delivery). */
public record UserContactResponse(Long id, String email, String firstName, Boolean enabled) {
}
