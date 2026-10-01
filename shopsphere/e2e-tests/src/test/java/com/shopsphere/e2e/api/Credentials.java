package com.shopsphere.e2e.api;

import java.util.Objects;

public record Credentials(String email, String password) {

    public Credentials {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(password, "password");
    }

    public Credentials withPassword(String otherPassword) {
        return new Credentials(email, otherPassword);
    }

    /** Never leak passwords into TestNG reports. */
    @Override
    public String toString() {
        return "Credentials[email=" + email + "]";
    }
}
