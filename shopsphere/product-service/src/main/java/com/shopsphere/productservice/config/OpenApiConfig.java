package com.shopsphere.productservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI productServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ShopSphere — Product Service API")
                        .description("""
                                REST APIs for managing the product catalog of ShopSphere.

                                Capabilities:
                                - Product CRUD (admin)
                                - Category management
                                - Search by name, filter by category, price range, availability
                                - Paginated product listing

                                This service is part of the ShopSphere microservices backend.
                                """)
                        .version("v0.0.1")
                        .contact(new Contact()
                                .name("ShopSphere Team")
                                .url("https://github.com/dhruvpatel/shopsphere"))
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Local development")
                ))
                .tags(List.of(
                        new Tag().name("Products").description("Product CRUD, search, and filtering"),
                        new Tag().name("Categories").description("Product category management"),
                        new Tag().name("Health").description("Service health endpoints")
                ));
    }
}
