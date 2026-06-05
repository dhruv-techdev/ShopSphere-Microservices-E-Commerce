package com.shopsphere.orderservice.client;

import com.shopsphere.orderservice.exception.CartUnavailableException;
import com.shopsphere.orderservice.exception.EmptyCartException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class CartClient {

    private static final String USER_HEADER = "X-User-Id";

    private final RestClient restClient;

    public CartClient(RestClient.Builder builder,
                      @Value("${app.cart-service.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public CartDto getCart(Long userId) {
        try {
            CartDto cart = restClient.get()
                    .uri("/api/v1/carts")
                    .header(USER_HEADER, String.valueOf(userId))
                    .retrieve()
                    .body(CartDto.class);
            if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
                throw new EmptyCartException(userId);
            }
            return cart;
        } catch (HttpClientErrorException ex) {
            log.error("Cart Service returned {} for user {}", ex.getStatusCode(), userId);
            throw new CartUnavailableException("Cart Service error: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            log.error("Cart Service unreachable for user {}: {}", userId, ex.getMessage());
            throw new CartUnavailableException("Cart Service is unreachable", ex);
        }
    }

    public void clearCart(Long userId) {
        try {
            restClient.delete()
                    .uri("/api/v1/carts/clear")
                    .header(USER_HEADER, String.valueOf(userId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            // 404 on clear after a successful order is harmless — log and move on
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("Cart for user {} was already empty when clearing post-order", userId);
                return;
            }
            log.error("Failed to clear cart for user {}: {}", userId, ex.getStatusCode());
            // Don't throw — order is already committed; cart cleanup failure is non-fatal
        } catch (ResourceAccessException ex) {
            log.error("Cart Service unreachable when clearing cart for user {}: {}", userId, ex.getMessage());
            // Same reasoning — don't fail the order
        }
    }
}
