package com.shopsphere.e2e.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.shopsphere.e2e.model.ProductDraft;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

/**
 * Thin client over the API gateway used to arrange test data, so UI tests only drive the screens under test.
 */
public final class ShopSphereApi {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final Function<String, URI> resolver;
    private final HttpClient http;
    private final ObjectMapper json;

    public ShopSphereApi(Function<String, URI> resolver) {
        this.resolver = resolver;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.json = JsonMapper.builder()
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    /* ------------------------------ auth ------------------------------ */

    /** Registers and signs in; registration alone returns no token while email verification is enforced. */
    public AuthSession register(NewUser user) {
        send("POST", "/api/v1/auth/register", null, user);
        return login(user.credentials());
    }

    public AuthSession login(Credentials credentials) {
        JsonNode body = send("POST", "/api/v1/auth/login", null,
                Map.of("email", credentials.email(), "password", credentials.password()));
        return new AuthSession(
                body.path("userId").asLong(),
                body.path("email").asText(),
                body.path("role").asText(),
                body.path("token").asText());
    }

    /* ------------------------------ catalog / inventory ------------------------------ */

    public long createProduct(AuthSession admin, ProductDraft draft) {
        JsonNode body = send("POST", "/api/v1/products", admin, new ProductRequest(
                draft.name(), draft.description(), draft.price(), draft.stock(), draft.active()));
        return requiredId(body, "product");
    }

    /** Idempotent: an existing inventory record (409) is fine. */
    public void initInventory(AuthSession admin, long productId, int quantity) {
        HttpResponse<String> response = exchange("POST", "/api/v1/inventory", admin,
                Map.of("productId", productId, "initialQuantity", quantity));
        if (response.statusCode() != 409) {
            requireSuccess("POST", response);
        }
    }

    /* ------------------------------ cart / orders ------------------------------ */

    public void addToCart(AuthSession customer, long productId, int quantity) {
        send("POST", "/api/v1/carts/items", customer, Map.of("productId", productId, "quantity", quantity));
    }

    public long placeOrder(AuthSession customer, ShippingAddress address) {
        JsonNode body = send("POST", "/api/v1/orders", customer, Map.of("shippingAddress", address));
        return requiredId(body, "order");
    }

    /* ------------------------------ plumbing ------------------------------ */

    /** product-service ProductRequest (category left unset). */
    record ProductRequest(String name, String description, BigDecimal price, int stockQuantity, boolean active) {
    }

    private JsonNode send(String method, String path, AuthSession session, Object body) {
        HttpResponse<String> response = exchange(method, path, session, body);
        requireSuccess(method, response);
        return parse(response.body());
    }

    private HttpResponse<String> exchange(String method, String path, AuthSession session, Object body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(resolver.apply(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(write(body)));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (session != null) {
            request.header("Authorization", session.bearer());
        }
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(method + " " + path + " failed — is the stack running?", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during " + method + " " + path, e);
        }
    }

    private static void requireSuccess(String method, HttpResponse<String> response) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new ApiException(method, response.uri(), status, response.body());
        }
    }

    private static long requiredId(JsonNode body, String what) {
        long id = body.path("id").asLong(0);
        if (id <= 0) {
            throw new IllegalStateException("Created " + what + " has no id: " + body);
        }
        return id;
    }

    private String write(Object body) {
        try {
            return json.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialise request body " + body, e);
        }
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return MissingNode.getInstance();
        }
        try {
            return json.readTree(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Response is not JSON: " + body, e);
        }
    }
}
