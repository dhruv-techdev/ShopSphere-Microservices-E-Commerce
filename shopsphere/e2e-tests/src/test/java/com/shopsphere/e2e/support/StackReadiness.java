package com.shopsphere.e2e.support;

import com.shopsphere.e2e.config.E2eConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Blocks until the admin app and every service the suite touches answer through the gateway. */
public final class StackReadiness {

    private static final System.Logger LOG = System.getLogger(StackReadiness.class.getName());
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration RETRY_EVERY = Duration.ofSeconds(3);

    private static final List<String> GATEWAY_PROBES = List.of(
            "/actuator/health",
            "/api/v1/users/health",
            "/api/v1/products/health",
            "/api/v1/carts/health",
            "/api/v1/orders/health",
            "/api/v1/inventory/health",
            "/api/v1/payments/health",
            "/api/v1/shipments/health");

    private StackReadiness() {
    }

    public static void await(E2eConfig config) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(PROBE_TIMEOUT).build();
        Set<URI> pending = new LinkedHashSet<>(Stream.concat(
                        GATEWAY_PROBES.stream().map(config::api),
                        Stream.of(URI.create(config.url("/"))))
                .toList());
        Instant deadline = Instant.now().plus(config.stackTimeout());

        while (true) {
            pending.removeIf(uri -> answers(http, uri));
            if (pending.isEmpty()) {
                LOG.log(System.Logger.Level.INFO, "ShopSphere stack is ready");
                return;
            }
            if (Instant.now().isAfter(deadline)) {
                throw new IllegalStateException("ShopSphere stack not ready after " + config.stackTimeout().toSeconds()
                        + "s, still waiting on " + pending + ". Start it with: docker compose -f shopsphere/docker-compose.yml "
                        + "-f shopsphere/docker-compose.e2e.yml up -d --build");
            }
            sleep(RETRY_EVERY);
        }
    }

    /** Any non-5xx answer means the gateway route and the service behind it are up. */
    private static boolean answers(HttpClient http, URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(PROBE_TIMEOUT).GET().build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() < 500;
        } catch (IOException notYetListening) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the stack", e);
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the stack", e);
        }
    }
}
