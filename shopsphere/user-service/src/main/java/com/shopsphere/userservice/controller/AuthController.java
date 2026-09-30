package com.shopsphere.userservice.controller;

import com.shopsphere.userservice.dto.AuthResponse;
import com.shopsphere.userservice.dto.EmailRequest;
import com.shopsphere.userservice.dto.LoginRequest;
import com.shopsphere.userservice.dto.PasswordResetConfirmRequest;
import com.shopsphere.userservice.dto.RefreshTokenRequest;
import com.shopsphere.userservice.dto.RegisterRequest;
import com.shopsphere.userservice.dto.TokenRequest;
import com.shopsphere.userservice.exception.ApiError;
import com.shopsphere.userservice.service.AuthService;
import com.shopsphere.userservice.service.EmailVerificationService;
import com.shopsphere.userservice.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, refresh, email verification and password reset.")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;

    /* ------------------------------ register / login ------------------------------ */

    @PostMapping("/register")
    @Operation(summary = "Register a new user",
            description = """
                    Creates an account and emails a verification link (valid 24h).
                    When email verification is required (default), no tokens are returned —
                    confirm the email first via POST /api/v1/auth/email-verification/confirm.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Email already registered",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login",
            description = """
                    Returns a short-lived access token (`token`, JWT) and a refresh token.
                    - 401 for unknown email / wrong password / disabled account (no enumeration).
                    - 403 `EMAIL_NOT_VERIFIED` when the password is right but the email isn't confirmed.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logged in"),
            @ApiResponse(responseCode = "401", description = "Invalid email or password",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Email not verified",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /* ------------------------------ refresh tokens ------------------------------ */

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token",
            description = """
                    Exchanges a refresh token for a new access token AND a new refresh token.
                    The presented refresh token is consumed. Re-using an old one revokes the whole session.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New token pair"),
            @ApiResponse(responseCode = "401", description = "Refresh token invalid, expired or reused",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Logout", description = "Revokes the session the refresh token belongs to. Always 204.")
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
    }

    /* ---------------------------- email verification ---------------------------- */

    @PostMapping("/email-verification/confirm")
    @Operation(summary = "Confirm email address",
            description = "Redeems the emailed token (single use) and returns a token pair.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verified and logged in"),
            @ApiResponse(responseCode = "400", description = "Link invalid or expired",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AuthResponse confirmEmail(@Valid @RequestBody TokenRequest request) {
        return emailVerificationService.confirm(request.token());
    }

    @PostMapping("/email-verification/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Resend verification email",
            description = "Always 202 — never reveals whether the account exists. Throttled per account.")
    public Map<String, String> resendVerification(@Valid @RequestBody EmailRequest request) {
        emailVerificationService.resend(request.email());
        return Map.of("message", "If an unverified account exists for that address, a new verification email is on its way.");
    }

    /* ------------------------------ password reset ------------------------------ */

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Request a password reset",
            description = "Always 202 — never reveals whether the account exists. Link valid 30 minutes, throttled per account.")
    public Map<String, String> requestPasswordReset(@Valid @RequestBody EmailRequest request) {
        passwordResetService.requestReset(request.email());
        return Map.of("message", "If an account exists for that address, a password reset email is on its way.");
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Set a new password",
            description = "Redeems the emailed token (single use), sets the password and signs out every session.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password changed"),
            @ApiResponse(responseCode = "400", description = "Link invalid/expired or password invalid",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirm(request.token(), request.newPassword());
    }
}
