package com.shopsphere.userservice.dto;

import com.shopsphere.userservice.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;
    /** Populated on login in US7. Null on registration. */
    private String token;
}
