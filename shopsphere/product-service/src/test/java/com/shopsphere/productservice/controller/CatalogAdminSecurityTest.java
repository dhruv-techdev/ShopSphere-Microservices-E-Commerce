package com.shopsphere.productservice.controller;

import com.shopsphere.productservice.dto.CategoryResponse;
import com.shopsphere.productservice.dto.ProductResponse;
import com.shopsphere.productservice.exception.CategoryInUseException;
import com.shopsphere.productservice.exception.DuplicateCategoryException;
import com.shopsphere.productservice.service.CategoryService;
import com.shopsphere.productservice.service.ProductService;
import com.shopsphere.security.ShopSphereSecurityAutoConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US42/US44 — catalog writes are ADMIN-only inside the service, not just at the gateway. */
@WebMvcTest(controllers = {ProductController.class, CategoryController.class},
        properties = {
                "spring.cloud.config.enabled=false",
                "app.jwt.secret=" + CatalogAdminSecurityTest.SECRET
        })
@ImportAutoConfiguration(ShopSphereSecurityAutoConfiguration.class)
class CatalogAdminSecurityTest {

    static final String SECRET = "product-test-secret-product-test-secret-0123456789";

    private static final String PRODUCT_JSON = """
            {"name":"Wireless Mouse","price":29.99,"stockQuantity":10}
            """;
    private static final String CATEGORY_JSON = """
            {"name":"Electronics"}
            """;

    @Autowired MockMvc mvc;
    @MockBean ProductService productService;
    @MockBean CategoryService categoryService;

    private static String bearer(long userId, String role) {
        return "Bearer " + Jwts.builder()
                .subject("u" + userId + "@example.com")
                .claim("userId", userId)
                .claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private static final String CUSTOMER = bearer(7, "CUSTOMER");
    private static final String ADMIN = bearer(1, "ADMIN");

    /* ------------------------------ products ------------------------------ */

    @Test
    void createProduct_asCustomer_is403_andServiceNotCalled() throws Exception {
        mvc.perform(post("/api/v1/products").header("Authorization", CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON))
                .andExpect(status().isForbidden());

        verify(productService, never()).create(any());
    }

    @Test
    void createProduct_withoutToken_is401() throws Exception {
        mvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createProduct_asAdmin_is201() throws Exception {
        when(productService.create(any())).thenReturn(ProductResponse.builder().id(5L).name("Wireless Mouse").build());

        mvc.perform(post("/api/v1/products").header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    void updateProduct_asCustomer_is403() throws Exception {
        mvc.perform(put("/api/v1/products/5").header("Authorization", CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON))
                .andExpect(status().isForbidden());

        verify(productService, never()).update(anyLong(), any());
    }

    @Test
    void deleteProduct_asCustomer_is403() throws Exception {
        mvc.perform(delete("/api/v1/products/5").header("Authorization", CUSTOMER))
                .andExpect(status().isForbidden());

        verify(productService, never()).delete(anyLong());
    }

    @Test
    void deleteProduct_asAdmin_is204() throws Exception {
        mvc.perform(delete("/api/v1/products/5").header("Authorization", ADMIN))
                .andExpect(status().isNoContent());
    }

    @Test
    void browsingProducts_staysPublic() throws Exception {
        when(productService.getById(5L)).thenReturn(ProductResponse.builder().id(5L).build());

        mvc.perform(get("/api/v1/products/5")).andExpect(status().isOk());
    }

    /* ----------------------------- categories ----------------------------- */

    @Test
    void createCategory_asCustomer_is403() throws Exception {
        mvc.perform(post("/api/v1/categories").header("Authorization", CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON).content(CATEGORY_JSON))
                .andExpect(status().isForbidden());

        verify(categoryService, never()).create(any());
    }

    @Test
    void createCategory_asAdmin_is201() throws Exception {
        when(categoryService.create(any())).thenReturn(CategoryResponse.builder().id(3L).name("Electronics").build());

        mvc.perform(post("/api/v1/categories").header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(CATEGORY_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    void listingCategories_staysPublic() throws Exception {
        when(categoryService.list()).thenReturn(List.of());

        mvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
    }

    @Test
    void gettingOneCategory_staysPublic() throws Exception {
        when(categoryService.getById(3L))
                .thenReturn(CategoryResponse.builder().id(3L).name("Electronics").productCount(2L).build());

        mvc.perform(get("/api/v1/categories/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCount").value(2));
    }

    @Test
    void updateCategory_asCustomer_is403() throws Exception {
        mvc.perform(put("/api/v1/categories/3").header("Authorization", CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON).content(CATEGORY_JSON))
                .andExpect(status().isForbidden());

        verify(categoryService, never()).update(anyLong(), any());
    }

    @Test
    void updateCategory_asAdmin_is200() throws Exception {
        when(categoryService.update(eq(3L), any()))
                .thenReturn(CategoryResponse.builder().id(3L).name("Electronics").build());

        mvc.perform(put("/api/v1/categories/3").header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(CATEGORY_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void updateCategory_toDuplicateName_is409() throws Exception {
        when(categoryService.update(eq(3L), any())).thenThrow(new DuplicateCategoryException("Electronics"));

        mvc.perform(put("/api/v1/categories/3").header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(CATEGORY_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteCategory_asCustomer_is403() throws Exception {
        mvc.perform(delete("/api/v1/categories/3").header("Authorization", CUSTOMER))
                .andExpect(status().isForbidden());

        verify(categoryService, never()).delete(anyLong());
    }

    @Test
    void deleteCategory_asAdmin_is204() throws Exception {
        mvc.perform(delete("/api/v1/categories/3").header("Authorization", ADMIN))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteCategory_stillInUse_is409() throws Exception {
        doThrow(new CategoryInUseException(3L, 2)).when(categoryService).delete(3L);

        mvc.perform(delete("/api/v1/categories/3").header("Authorization", ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("2 product(s)")));
    }
}
