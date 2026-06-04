package com.shopsphere.userservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI userServiceOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("ShopSphere — User Service API")
                .description("APIs for user registration, authentication, and roles")
                .version("v0.0.1"));
    }
}
