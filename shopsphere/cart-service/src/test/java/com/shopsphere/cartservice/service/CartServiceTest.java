package com.shopsphere.cartservice.service;

import com.shopsphere.cartservice.client.ProductClient;
import com.shopsphere.cartservice.client.ProductDto;
import com.shopsphere.cartservice.dto.AddCartItemRequest;
import com.shopsphere.cartservice.dto.CartResponse;
import com.shopsphere.cartservice.exception.ProductUnavailableException;
import com.shopsphere.cartservice.model.Cart;
import com.shopsphere.cartservice.repository.CartRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock CartRepository cartRepository;
    @Mock ProductClient productClient;

    @InjectMocks CartService cartService;

    @Test
    void addItem_inactiveProduct_throws() {
        ProductDto dto = new ProductDto(1L, "Gone", BigDecimal.TEN, 10, false);
        when(productClient.fetchProduct(1L)).thenReturn(dto);

        AddCartItemRequest req = new AddCartItemRequest(1L, 1);

        assertThatThrownBy(() -> cartService.addItem(7L, req))
                .isInstanceOf(ProductUnavailableException.class);
    }

    @Test
    void addItem_newCart_persistsAndReturns() {
        ProductDto dto = new ProductDto(1L, "Mouse", new BigDecimal("20.00"), 10, true);
        when(productClient.fetchProduct(1L)).thenReturn(dto);
        when(cartRepository.findByUserId(7L)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        CartResponse response = cartService.addItem(7L, new AddCartItemRequest(1L, 2));

        assertThat(response.getUserId()).isEqualTo(7L);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(response.getTotal()).isEqualByComparingTo("40.00");
    }
}
