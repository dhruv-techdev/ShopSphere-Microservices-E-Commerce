package com.shopsphere.security;

import com.shopsphere.security.testapp.SecurityProbeApplication;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SecurityProbeApplication.class,
        properties = "app.jwt.secret=" + SecurityAutoConfigurationTest.SECRET)
@AutoConfigureMockMvc
class SecurityAutoConfigurationTest {

    static final String SECRET = "test-secret-test-secret-test-secret-test-secret-0123456789";

    @Autowired MockMvc mvc;

    static String token(long userId, String role, String secret, long ttlMs) {
        return Jwts.builder()
                .subject("user" + userId + "@example.com")
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    static String bearer(long userId, String role) {
        return "Bearer " + token(userId, role, SECRET, 60_000);
    }

    @Test
    void publicEndpoint_needsNoToken() throws Exception {
        mvc.perform(get("/public")).andExpect(status().isOk());
    }

    @Test
    void adminEndpoint_withoutToken_is401() throws Exception {
        mvc.perform(post("/admin"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void adminEndpoint_asCustomer_is403() throws Exception {
        mvc.perform(post("/admin").header("Authorization", bearer(7, "CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void adminEndpoint_asAdmin_isAllowed() throws Exception {
        mvc.perform(post("/admin").header("Authorization", bearer(1, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string("admin"));
    }

    @Test
    void spoofedRoleHeader_isIgnored() throws Exception {
        mvc.perform(post("/admin")
                        .header("Authorization", bearer(7, "CUSTOMER"))
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    void expiredToken_isTreatedAsAnonymous() throws Exception {
        String expired = "Bearer " + token(1, "ADMIN", SECRET, -60_000);

        mvc.perform(post("/admin").header("Authorization", expired)).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherSecret_isRejected() throws Exception {
        String forged = "Bearer " + token(1, "ADMIN", "another-secret-another-secret-another-secret-0123456789", 60_000);

        mvc.perform(post("/admin").header("Authorization", forged)).andExpect(status().isUnauthorized());
    }

    @Test
    void principalCarriesClaims() throws Exception {
        mvc.perform(get("/me").header("Authorization", bearer(42, "customer")))
                .andExpect(status().isOk())
                .andExpect(content().string("42:user42@example.com:CUSTOMER"));
    }
}
