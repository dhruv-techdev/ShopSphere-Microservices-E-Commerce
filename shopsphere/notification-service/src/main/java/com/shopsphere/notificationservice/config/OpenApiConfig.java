package com.shopsphere.notificationservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationServiceOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("ShopSphere — Notification Service API")
                .description("Simulated notification logs from order and payment events")
                .version("v0.0.1"));
    }
}
