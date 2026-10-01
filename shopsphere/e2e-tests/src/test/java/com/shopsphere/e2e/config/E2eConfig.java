package com.shopsphere.e2e.config;

import com.shopsphere.e2e.api.Credentials;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Suite settings. Each key resolves from a JVM system property first ({@code -De2e.baseUrl=...}),
 * then from the matching environment variable ({@code E2E_BASE_URL}), then the default.
 */
public record E2eConfig(
        URI baseUrl,
        URI apiUrl,
        boolean headless,
        Optional<Path> chromeBinary,
        Duration uiTimeout,
        Duration asyncTimeout,
        Duration stackTimeout,
        Path artifactsDir,
        Optional<Credentials> admin) {

    private static final E2eConfig INSTANCE = load();

    public static E2eConfig get() {
        return INSTANCE;
    }

    /** Absolute admin-app URL for an app-relative path such as {@code /products}. */
    public String url(String path) {
        return baseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    /** Absolute gateway URL for a path such as {@code /api/v1/products}. */
    public URI api(String path) {
        return URI.create(apiUrl + (path.startsWith("/") ? path : "/" + path));
    }

    static E2eConfig load() {
        return new E2eConfig(
                uri(setting("e2e.baseUrl", "http://localhost:4200")),
                uri(setting("e2e.apiUrl", "http://localhost:8080")),
                Boolean.parseBoolean(setting("e2e.headless", "true")),
                optional("e2e.chromeBinary").map(Path::of),
                seconds("e2e.uiTimeoutSeconds", 15),
                seconds("e2e.asyncTimeoutSeconds", 90),
                seconds("e2e.stackTimeoutSeconds", 180),
                Path.of(setting("e2e.artifactsDir", "target/e2e-artifacts")).toAbsolutePath(),
                adminCredentials());
    }

    /** {@code e2e.adminEmail} → {@code E2E_ADMIN_EMAIL}. */
    static String envName(String key) {
        return key.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace('.', '_')
                .toUpperCase(Locale.ROOT);
    }

    private static Optional<Credentials> adminCredentials() {
        Optional<String> email = optional("e2e.adminEmail");
        Optional<String> password = optional("e2e.adminPassword");
        if (email.isPresent() != password.isPresent()) {
            throw new IllegalStateException("Set both e2e.adminEmail and e2e.adminPassword, or neither "
                    + "(the suite then registers a throwaway ADMIN account).");
        }
        return email.map(e -> new Credentials(e, password.orElseThrow()));
    }

    private static String setting(String key, String fallback) {
        return optional(key).orElse(fallback);
    }

    private static Optional<String> optional(String key) {
        return Stream.of(System.getProperty(key), System.getenv(envName(key)))
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .findFirst();
    }

    private static Duration seconds(String key, long fallback) {
        String raw = setting(key, Long.toString(fallback));
        try {
            long value = Long.parseLong(raw);
            if (value <= 0) {
                throw new NumberFormatException("must be positive");
            }
            return Duration.ofSeconds(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(key + " must be a positive number of seconds, got '" + raw + "'", e);
        }
    }

    private static URI uri(String raw) {
        String trimmed = raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
        return URI.create(trimmed);
    }
}
