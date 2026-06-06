package com.shopsphere.productservice.service;

import com.shopsphere.productservice.dto.ProductRequest;
import com.shopsphere.productservice.dto.ProductResponse;
import com.shopsphere.productservice.entity.Product;
import com.shopsphere.productservice.exception.ProductNotFoundException;
import com.shopsphere.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
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
class ProductServiceTest {

    @Mock
    ProductRepository productRepository;

    @Mock
    CategoryService categoryService;

    @InjectMocks
    ProductService productService;

    private ProductRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = ProductRequest.builder()
                .name("Test")
                .price(new BigDecimal("10.00"))
                .stockQuantity(5)
                .active(true)
                .build();
    }

    @Test
    void create_savesProductAndReturnsResponse() {
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        ProductResponse response = productService.create(validRequest);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Test");
        assertThat(response.getPrice()).isEqualByComparingTo("10.00");
    }

    @Test
    void getById_whenNotFound_throwsProductNotFoundException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getById(99L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");
    }
}
