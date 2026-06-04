package com.shopsphere.productservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI productServiceOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("ShopSphere — Product Service API")
                .description("APIs for product catalog, categories, and search")
                .version("v0.0.1"));
    }
}
