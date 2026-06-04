package com.shopsphere.userservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI userServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ShopSphere — User Service API")
                        .description("""
                                REST APIs for user registration, authentication, and profile.

                                Capabilities:
                                - User registration with role assignment
                                - Login returning a signed JWT (HS256)
                                - Profile lookup for the authenticated user
                                - BCrypt-encoded passwords, stateless sessions

                                Most endpoints require a Bearer token obtained from /auth/login.
                                Click the **Authorize** button and paste the token (no 'Bearer' prefix needed) to test protected endpoints.
                                """)
                        .version("v0.0.1")
                        .contact(new Contact()
                                .name("ShopSphere Team")
                                .url("https://github.com/dhruvpatel/shopsphere"))
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8082").description("Local development")
                ))
                .tags(List.of(
                        new Tag().name("Authentication").description("Register and login. Returns JWT tokens."),
                        new Tag().name("Users").description("Authenticated user profile and health endpoints")
                ))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the JWT returned from /api/v1/auth/login")));
    }
}
