package com.shopsphere.paymentservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentServiceOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("ShopSphere — Payment Service API")
                .description("Simulated payment processing. Not a real gateway.")
                .version("v0.0.1"));
    }
}
