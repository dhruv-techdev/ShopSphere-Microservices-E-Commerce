package com.shopsphere.cartservice.service;

import com.shopsphere.cartservice.client.ProductClient;
import com.shopsphere.cartservice.client.ProductDto;
import com.shopsphere.cartservice.dto.AddCartItemRequest;
import com.shopsphere.cartservice.dto.CartResponse;
import com.shopsphere.cartservice.exception.ProductUnavailableException;
import com.shopsphere.cartservice.model.Cart;
import com.shopsphere.cartservice.model.CartItem;
import com.shopsphere.cartservice.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final ProductClient productClient;

    public CartResponse addItem(Long userId, AddCartItemRequest request) {
        // 1. Fetch product from product-service (validates existence — ST4)
        ProductDto product = productClient.fetchProduct(request.getProductId());

        // 2. Reject inactive products
        if (Boolean.FALSE.equals(product.getActive())) {
            throw new ProductUnavailableException(product.getId());
        }

        // 3. Load existing cart or create new one
        Cart cart = cartRepository.findByUserId(userId).orElseGet(() ->
                Cart.builder()
                        .userId(userId)
                        .items(new ArrayList<>())
                        .createdAt(Instant.now())
                        .build()
        );

        // 4. ST7 — increment quantity if item already in cart; otherwise add new line
        CartItem existing = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(product.getId()))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + request.getQuantity());
            // refresh snapshot fields in case the product was updated since the last add
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

        cart.setUpdatedAt(Instant.now());
        cartRepository.save(cart);

        return toResponse(cart);
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
