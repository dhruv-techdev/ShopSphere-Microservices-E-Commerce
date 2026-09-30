package com.shopsphere.userservice.auth;

/** Single-use, emailed tokens. */
public enum TokenPurpose {
    EMAIL_VERIFICATION("auth:verify:"),
    PASSWORD_RESET("auth:pwreset:");

    private final String keyPrefix;

    TokenPurpose(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String keyPrefix() {
        return keyPrefix;
    }
}
