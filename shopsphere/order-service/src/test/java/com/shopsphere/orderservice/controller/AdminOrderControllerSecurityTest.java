package com.shopsphere.orderservice.controller;

import com.shopsphere.orderservice.dto.OrderResponse;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.exception.OrderStatusConflictException;
import com.shopsphere.orderservice.service.AdminOrderService;
import com.shopsphere.security.AuthenticatedUser;
import com.shopsphere.security.ShopSphereSecurityAutoConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US42 — every admin order endpoint: 401 anonymous, 403 customer, allowed for ADMIN. */
@WebMvcTest(controllers = AdminOrderController.class,
        properties = {
                "spring.cloud.config.enabled=false",
                "app.jwt.secret=" + AdminOrderControllerSecurityTest.SECRET
        })
@ImportAutoConfiguration(ShopSphereSecurityAutoConfiguration.class)
class AdminOrderControllerSecurityTest {

    static final String SECRET = "order-test-secret-order-test-secret-order-0123456789";

    @Autowired MockMvc mvc;
    @MockBean AdminOrderService adminOrderService;

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

    @Test
    void list_withoutToken_is401() throws Exception {
        mvc.perform(get("/api/v1/admin/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void list_asCustomer_is403() throws Exception {
        mvc.perform(get("/api/v1/admin/orders").header("Authorization", CUSTOMER))
                .andExpect(status().isForbidden());

        verify(adminOrderService, never()).search(any(), any(), any());
    }

    @Test
    void list_asAdmin_is200() throws Exception {
        when(adminOrderService.search(any(), any(), any())).thenReturn(Page.empty());

        mvc.perform(get("/api/v1/admin/orders").param("status", "PAID").header("Authorization", ADMIN))
                .andExpect(status().isOk());

        verify(adminOrderService).search(eq(OrderStatus.PAID), eq(null), any());
    }

    @Test
    void get_asCustomer_is403() throws Exception {
        mvc.perform(get("/api/v1/admin/orders/42").header("Authorization", CUSTOMER))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancel_asCustomer_is403_andNothingCancelled() throws Exception {
        mvc.perform(post("/api/v1/admin/orders/42/cancel").header("Authorization", CUSTOMER))
                .andExpect(status().isForbidden());

        verify(adminOrderService, never()).cancel(any(), any());
    }

    @Test
    void cancel_asAdmin_passesTheAdminIdentity() throws Exception {
        when(adminOrderService.cancel(eq(42L), any()))
                .thenReturn(OrderResponse.builder().id(42L).status(OrderStatus.CANCELLED).build());

        mvc.perform(post("/api/v1/admin/orders/42/cancel").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(adminOrderService).cancel(42L, new AuthenticatedUser(1L, "u1@example.com", "ADMIN"));
    }

    @Test
    void cancel_shippedOrder_is409() throws Exception {
        when(adminOrderService.cancel(eq(42L), any()))
                .thenThrow(new OrderStatusConflictException(42L, OrderStatus.SHIPPED, "cancelled"));

        mvc.perform(post("/api/v1/admin/orders/42/cancel").header("Authorization", ADMIN))
                .andExpect(status().isConflict());
    }
}
