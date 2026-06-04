package com.shopsphere.userservice.controller;

import com.shopsphere.userservice.dto.UserResponse;
import com.shopsphere.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile and health endpoints")
public class UserController {

    private final UserService userService;

    @GetMapping("/health")
    @Operation(summary = "Health check (public)")
    public Map<String, Object> health() {
        return Map.of(
                "service", "user-service",
                "status", "UP",
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user's profile", security = @SecurityRequirement(name = "bearerAuth"))
    public UserResponse me(@AuthenticationPrincipal String email) {
        return userService.getProfile(email);
    }
}
