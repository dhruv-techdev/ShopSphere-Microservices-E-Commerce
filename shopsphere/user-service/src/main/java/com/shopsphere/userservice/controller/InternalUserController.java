package com.shopsphere.userservice.controller;

import com.shopsphere.userservice.dto.UserContactResponse;
import com.shopsphere.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * US39 — service-to-service endpoints. Not routed by the API Gateway (it only forwards
 * /api/v1/**) and guarded by a shared secret. Fails closed if no token is configured.
 */
@Hidden
@RestController
@RequestMapping("/internal/v1/users")
public class InternalUserController {

    static final String TOKEN_HEADER = "X-Internal-Token";

    private final UserService userService;
    private final byte[] expectedToken;

    public InternalUserController(UserService userService,
                                  @Value("${app.internal.token:${INTERNAL_API_TOKEN:}}") String internalToken) {
        this.userService = userService;
        this.expectedToken = internalToken.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/{userId}/contact")
    public ResponseEntity<UserContactResponse> contact(
            @PathVariable Long userId,
            @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        if (!authorized(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(userService.getContact(userId));
    }

    private boolean authorized(String presented) {
        if (expectedToken.length == 0 || presented == null) {
            return false;
        }
        // constant-time comparison
        return MessageDigest.isEqual(expectedToken, presented.getBytes(StandardCharsets.UTF_8));
    }
}
