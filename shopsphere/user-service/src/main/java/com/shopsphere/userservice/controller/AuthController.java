package com.shopsphere.userservice.controller;

import com.shopsphere.userservice.dto.AuthResponse;
import com.shopsphere.userservice.dto.LoginRequest;
import com.shopsphere.userservice.dto.RegisterRequest;
import com.shopsphere.userservice.exception.ApiError;
import com.shopsphere.userservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register and login. Returns JWT tokens.")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(
            summary = "Register a new user",
            description = """
                    Creates a new user account.

                    - Email must be unique (case-insensitive).
                    - Password is BCrypt-encoded before storage.
                    - If `role` is omitted, defaults to `CUSTOMER`.
                    - On success a signed JWT is returned in the response.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created. JWT returned in `token`."),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Email already registered",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<AuthResponse> register(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "firstName": "Alice",
                              "lastName": "Smith",
                              "email": "alice@example.com",
                              "password": "secret123",
                              "role": "CUSTOMER"
                            }
                            """))
            )
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Login and receive a JWT",
            description = """
                    Authenticates email + password and returns a signed JWT.

                    - Same `401 Unauthorized` is returned for both unknown emails and wrong passwords (prevents email enumeration).
                    - Disabled accounts also receive `401`.
                    - The token's `Authorization: Bearer <token>` header should be sent on all protected endpoints.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful. JWT returned in `token`."),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Invalid email or password",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AuthResponse login(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "email": "alice@example.com",
                              "password": "secret123"
                            }
                            """))
            )
            @Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
