package com.shopsphere.userservice.dto;

import com.shopsphere.userservice.entity.Role;
import com.shopsphere.userservice.entity.User;

import java.time.Instant;

/** US46 — user row for the admin console (never includes the password hash). */
public record AdminUserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        Role role,
        boolean enabled,
        boolean emailVerified,
        Instant emailVerifiedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminUserResponse from(User u) {
        return new AdminUserResponse(u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(), u.getRole(),
                Boolean.TRUE.equals(u.getEnabled()), Boolean.TRUE.equals(u.getEmailVerified()),
                u.getEmailVerifiedAt(), u.getCreatedAt(), u.getUpdatedAt());
    }
}
