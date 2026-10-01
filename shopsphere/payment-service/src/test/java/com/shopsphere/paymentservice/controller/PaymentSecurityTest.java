package com.shopsphere.paymentservice.controller;

import com.shopsphere.paymentservice.dto.PaymentResponse;
import com.shopsphere.paymentservice.entity.PaymentStatus;
import com.shopsphere.paymentservice.service.AdminPaymentService;
import com.shopsphere.paymentservice.service.PaymentService;
import com.shopsphere.security.ShopSphereSecurityAutoConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US46 — admin payment endpoints and the locked-down simulator/reads. */
@WebMvcTest(controllers = {AdminPaymentController.class, PaymentController.class},
        properties = {
                "spring.cloud.config.enabled=false",
                "app.jwt.secret=" + PaymentSecurityTest.SECRET
        })
@ImportAutoConfiguration(ShopSphereSecurityAutoConfiguration.class)
class PaymentSecurityTest {

    static final String SECRET = "payment-test-secret-payment-test-secret-0123456789";

    @Autowired MockMvc mvc;
    @MockBean PaymentService paymentService;
    @MockBean AdminPaymentService adminPaymentService;

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

    private static PaymentResponse payment(String ref, long userId) {
        return PaymentResponse.builder().paymentReference(ref).orderId(42L).userId(userId)
                .amount(new BigDecimal("10.00")).status(PaymentStatus.SUCCESSFUL).build();
    }

    @Test
    void adminList_asAdmin_is200() throws Exception {
        when(adminPaymentService.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(payment("r1", 7)), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/admin/payments").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].paymentReference").value("r1"));
    }

    @Test
    void adminEndpoints_asCustomer_are403() throws Exception {
        mvc.perform(get("/api/v1/admin/payments/summary").header("Authorization", CUSTOMER))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/payments/duplicates").header("Authorization", CUSTOMER))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoints_anonymous_are401() throws Exception {
        mvc.perform(get("/api/v1/admin/payments")).andExpect(status().isUnauthorized());
    }

    @Test
    void simulate_asCustomer_is403_andNothingIsCharged() throws Exception {
        mvc.perform(post("/api/v1/payments/simulate").header("Authorization", CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":1,\"userId\":7,\"amount\":10}"))
                .andExpect(status().isForbidden());

        verify(paymentService, never()).simulate(any());
    }

    @Test
    void listByOrder_customerOnlySeesOwnPayments() throws Exception {
        when(paymentService.getByOrderId(42L)).thenReturn(List.of(payment("mine", 7), payment("theirs", 8)));

        mvc.perform(get("/api/v1/payments").param("orderId", "42").header("Authorization", CUSTOMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].paymentReference").value("mine"));
    }

    @Test
    void getByReference_otherUsersPayment_is403() throws Exception {
        when(paymentService.getByReference("theirs")).thenReturn(payment("theirs", 8));

        mvc.perform(get("/api/v1/payments/theirs").header("Authorization", CUSTOMER))
                .andExpect(status().isForbidden());
    }
}
