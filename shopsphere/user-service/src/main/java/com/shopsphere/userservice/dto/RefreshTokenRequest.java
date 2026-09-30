package com.shopsphere.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken is required")
        @Size(max = 200, message = "refreshToken is too long")
        String refreshToken) {
}
