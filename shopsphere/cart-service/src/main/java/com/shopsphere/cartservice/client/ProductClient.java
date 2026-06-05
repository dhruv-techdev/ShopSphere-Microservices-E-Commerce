package com.shopsphere.cartservice.client;

import com.shopsphere.cartservice.exception.ProductLookupException;
import com.shopsphere.cartservice.exception.ProductNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class ProductClient {

    private final RestClient restClient;

    public ProductClient(RestClient.Builder builder,
                         @Value("${app.product-service.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public ProductDto fetchProduct(Long productId) {
        try {
            return restClient.get()
                    .uri("/api/v1/products/{id}", productId)
                    .retrieve()
                    .body(ProductDto.class);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ProductNotFoundException(productId);
            }
            log.error("Product Service returned {} for product {}", ex.getStatusCode(), productId);
            throw new ProductLookupException(
                    "Product Service error: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            log.error("Product Service unreachable when fetching product {}: {}",
                    productId, ex.getMessage());
            throw new ProductLookupException(
                    "Product Service is unreachable", ex);
        }
    }
}
