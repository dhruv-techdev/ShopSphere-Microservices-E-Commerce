package com.shopsphere.e2e.api;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** Body of POST /api/v1/auth/register. */
public record NewUser(String firstName, String lastName, String email, String password, String role) {

    public static NewUser admin(String email, String password) {
        return new NewUser("E2E", "Admin", email, password, "ADMIN");
    }

    public static NewUser customer(String email, String password) {
        return new NewUser("E2E", "Customer", email, password, "CUSTOMER");
    }

    @JsonIgnore
    public Credentials credentials() {
        return new Credentials(email, password);
    }

    @Override
    public String toString() {
        return "NewUser[email=" + email + ", role=" + role + "]";
    }
}
