package com.shopsphere.userservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shopsphere.userservice.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;
    private Boolean emailVerified;

    /** Access token (JWT). Name kept as `token` for existing clients. Null until the email is verified. */
    private String token;
    private String tokenType;
    /** Access-token lifetime in seconds. */
    private Long expiresIn;

    /** US41 — opaque, single-use; exchange at POST /api/v1/auth/refresh. */
    private String refreshToken;
    private Long refreshExpiresIn;

    /** Human-readable hint, e.g. "check your email". */
    private String message;
}
