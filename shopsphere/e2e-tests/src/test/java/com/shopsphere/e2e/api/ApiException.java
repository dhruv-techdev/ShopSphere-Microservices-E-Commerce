package com.shopsphere.e2e.api;

import java.net.URI;

/** A seeding call through the gateway did not return the expected status. */
public final class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private static final int MAX_BODY = 500;

    private final int status;
    private final String body;

    public ApiException(String method, URI uri, int status, String body) {
        super(method + " " + uri + " -> HTTP " + status + ": " + abbreviate(body));
        this.status = status;
        this.body = body;
    }

    public int status() {
        return status;
    }

    public String body() {
        return body;
    }

    private static String abbreviate(String body) {
        if (body == null || body.isBlank()) {
            return "<empty body>";
        }
        return body.length() <= MAX_BODY ? body : body.substring(0, MAX_BODY) + "…";
    }
}
