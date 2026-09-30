package com.shopsphere.gateway.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPathsTest {

    private static SecurityPaths paths() {
        SecurityPaths paths = new SecurityPaths();
        paths.setPublicPaths(List.of("/api/v1/auth/login"));
        paths.setPublicGetPrefixes(List.of("/api/v1/products"));
        paths.setAdminWritePrefixes(List.of("/api/v1/products", "/api/v1/inventory"));
        paths.setAdminPrefixes(List.of("/api/v1/admin"));
        return paths;
    }

    @Test
    void adminPrefix_requiresAdminForEveryMethod() {
        assertThat(paths().requiresAdmin("/api/v1/admin/orders", "GET")).isTrue();
        assertThat(paths().requiresAdmin("/api/v1/admin/orders/5/cancel", "POST")).isTrue();
    }

    @Test
    void adminWritePrefix_requiresAdminForWritesOnly() {
        assertThat(paths().requiresAdmin("/api/v1/products/5", "PUT")).isTrue();
        assertThat(paths().requiresAdmin("/api/v1/products/5", "GET")).isFalse();
    }

    @Test
    void prefixMatching_isSegmentAware() {
        assertThat(paths().requiresAdmin("/api/v1/administrator", "GET")).isFalse();
        assertThat(paths().isPublic("/api/v1/products-internal", "GET")).isFalse();
        assertThat(paths().isPublic("/api/v1/products/5", "GET")).isTrue();
    }
}
