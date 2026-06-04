package com.shopsphere.userservice.controller;

import com.shopsphere.userservice.dto.UserResponse;
import com.shopsphere.userservice.exception.ApiError;
import com.shopsphere.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "Users", description = "Authenticated user profile and health endpoints")
public class UserController {

    private final UserService userService;

    @GetMapping("/health")
    @Operation(
            summary = "Health check",
            description = "Public endpoint. Returns service status and current server timestamp."
    )
    public Map<String, Object> health() {
        return Map.of(
                "service", "user-service",
                "status", "UP",
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/me")
    @Operation(
            summary = "Get current user profile",
            description = "Returns the authenticated user's profile derived from the JWT subject claim.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "User no longer exists",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public UserResponse me(@AuthenticationPrincipal String email) {
        return userService.getProfile(email);
    }
}
