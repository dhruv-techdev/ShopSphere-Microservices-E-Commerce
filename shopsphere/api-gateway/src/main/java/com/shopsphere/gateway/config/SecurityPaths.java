package com.shopsphere.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.security")
public class SecurityPaths {

    /** Exact paths reachable without a JWT. */
    private List<String> publicPaths = new ArrayList<>();

    /** GET under these prefixes needs no JWT (catalog browsing). */
    private List<String> publicGetPrefixes = new ArrayList<>();

    /** POST/PUT/PATCH/DELETE under these prefixes require ADMIN. */
    private List<String> adminWritePrefixes = new ArrayList<>();

    /** US42 — every method under these prefixes requires ADMIN (e.g. /api/v1/admin). */
    private List<String> adminPrefixes = new ArrayList<>();

    public boolean isPublic(String path, String method) {
        if (publicPaths.contains(path)) {
            return true;
        }
        return "GET".equalsIgnoreCase(method) && matchesAny(publicGetPrefixes, path);
    }

    public boolean requiresAdmin(String path, String method) {
        if (matchesAny(adminPrefixes, path)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method)) {
            return false;
        }
        return matchesAny(adminWritePrefixes, path);
    }

    /** Segment-aware prefix match: /api/v1/products matches /api/v1/products/5 but not /api/v1/products-x. */
    static boolean matchesAny(List<String> prefixes, String path) {
        return prefixes.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }
}
