package com.shopsphere.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TokenRequest(
        @NotBlank(message = "token is required")
        @Size(max = 200, message = "token is too long")
        String token) {
}
