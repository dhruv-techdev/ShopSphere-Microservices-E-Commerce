package com.shopsphere.cartservice.service;

import com.shopsphere.cartservice.client.ProductClient;
import com.shopsphere.cartservice.client.ProductDto;
import com.shopsphere.cartservice.dto.AddCartItemRequest;
import com.shopsphere.cartservice.dto.CartResponse;
import com.shopsphere.cartservice.dto.UpdateCartItemRequest;
import com.shopsphere.cartservice.exception.CartItemNotFoundException;
import com.shopsphere.cartservice.exception.CartNotFoundException;
import com.shopsphere.cartservice.exception.ProductUnavailableException;
import com.shopsphere.cartservice.model.Cart;
import com.shopsphere.cartservice.model.CartItem;
import com.shopsphere.cartservice.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final ProductClient productClient;

    /* ---------------------------------------------------------------- */
    /* Add to cart (US10)                                                */
    /* ---------------------------------------------------------------- */

    public CartResponse addItem(Long userId, AddCartItemRequest request) {
        ProductDto product = productClient.fetchProduct(request.getProductId());

        if (Boolean.FALSE.equals(product.getActive())) {
            throw new ProductUnavailableException(product.getId());
        }

        Cart cart = cartRepository.findByUserId(userId).orElseGet(() ->
                Cart.builder()
                        .userId(userId)
                        .items(new ArrayList<>())
                        .createdAt(Instant.now())
                        .build()
        );

        CartItem existing = findItem(cart, product.getId());

        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + request.getQuantity());
            existing.setName(product.getName());
            existing.setUnitPrice(product.getPrice());
            log.debug("Incremented quantity for product {} in cart {} to {}",
                    product.getId(), userId, existing.getQuantity());
        } else {
            cart.getItems().add(CartItem.builder()
                    .productId(product.getId())
                    .name(product.getName())
                    .unitPrice(product.getPrice())
                    .quantity(request.getQuantity())
                    .build());
            log.debug("Added new product {} to cart {}", product.getId(), userId);
        }

        return saveAndRespond(cart);
    }

    /* ---------------------------------------------------------------- */
    /* ST3 — Update quantity                                             */
    /* ---------------------------------------------------------------- */

    public CartResponse updateItemQuantity(Long userId, Long productId, UpdateCartItemRequest request) {
        Cart cart = loadCart(userId);
        CartItem item = findItem(cart, productId);
        if (item == null) {
            throw new CartItemNotFoundException(userId, productId);
        }

        // Re-fetch product to refresh snapshot fields and reject inactive products
        ProductDto product = productClient.fetchProduct(productId);
        if (Boolean.FALSE.equals(product.getActive())) {
            throw new ProductUnavailableException(productId);
        }

        item.setQuantity(request.getQuantity());
        item.setName(product.getName());
        item.setUnitPrice(product.getPrice());
        log.debug("Updated quantity for product {} in cart {} to {}",
                productId, userId, request.getQuantity());

        return saveAndRespond(cart);
    }

    /* ---------------------------------------------------------------- */
    /* ST5 — Remove a single item                                        */
    /* ---------------------------------------------------------------- */

    public CartResponse removeItem(Long userId, Long productId) {
        Cart cart = loadCart(userId);

        boolean removed = false;
        Iterator<CartItem> it = cart.getItems().iterator();
        while (it.hasNext()) {
            if (it.next().getProductId().equals(productId)) {
                it.remove();
                removed = true;
                break;
            }
        }
        if (!removed) {
            throw new CartItemNotFoundException(userId, productId);
        }

        log.debug("Removed product {} from cart {}", productId, userId);

        if (cart.getItems().isEmpty()) {
            // Empty cart — just delete the Redis key entirely
            cartRepository.delete(userId);
            log.debug("Cart {} is now empty and was deleted", userId);
            return emptyCartResponse(userId);
        }

        return saveAndRespond(cart);
    }

    /* ---------------------------------------------------------------- */
    /* ST6 + ST7 — View cart                                             */
    /* ---------------------------------------------------------------- */

    public CartResponse getCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElseGet(() -> emptyCartResponse(userId));
    }

    /* ---------------------------------------------------------------- */
    /* ST8 — Clear cart                                                  */
    /* ---------------------------------------------------------------- */

    public void clearCart(Long userId) {
        if (!cartRepository.exists(userId)) {
            throw new CartNotFoundException(userId);
        }
        cartRepository.delete(userId);
        log.debug("Cleared cart {}", userId);
    }

    /* ---------------------------------------------------------------- */
    /* Helpers                                                           */
    /* ---------------------------------------------------------------- */

    private Cart loadCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException(userId));
    }

    private CartItem findItem(Cart cart, Long productId) {
        return cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst()
                .orElse(null);
    }

    private CartResponse saveAndRespond(Cart cart) {
        cart.setUpdatedAt(Instant.now());
        cartRepository.save(cart);
        return toResponse(cart);
    }

    private CartResponse emptyCartResponse(Long userId) {
        return CartResponse.builder()
                .userId(userId)
                .items(new ArrayList<>())
                .total(java.math.BigDecimal.ZERO)
                .itemCount(0)
                .build();
    }

    public CartResponse toResponse(Cart cart) {
        return CartResponse.builder()
                .userId(cart.getUserId())
                .items(cart.getItems())
                .total(cart.getTotal())
                .itemCount(cart.getItemCount())
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .build();
    }
}
