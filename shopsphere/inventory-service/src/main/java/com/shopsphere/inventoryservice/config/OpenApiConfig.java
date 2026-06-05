package com.shopsphere.inventoryservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI inventoryServiceOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("ShopSphere — Inventory Service API")
                .description("APIs for tracking and updating product stock")
                .version("v0.0.1"));
    }
}
