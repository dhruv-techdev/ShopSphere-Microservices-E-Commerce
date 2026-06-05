package com.shopsphere.orderservice.client;

import com.shopsphere.orderservice.exception.InventoryUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@Slf4j
public class InventoryClient {

    private final RestClient restClient;

    public InventoryClient(RestClient.Builder builder,
                           @Value("${app.inventory-service.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public AvailabilityResult checkAvailability(List<Item> items) {
        try {
            AvailabilityResponseDto response = restClient.post()
                    .uri("/api/v1/inventory/check-availability")
                    .body(new AvailabilityRequestDto(items))
                    .retrieve()
                    .body(AvailabilityResponseDto.class);
            if (response == null) {
                throw new InventoryUnavailableException("Empty response from Inventory Service", null);
            }
            return new AvailabilityResult(response.isAllAvailable(), response.getItems());
        } catch (HttpClientErrorException ex) {
            log.error("Inventory Service returned {}: {}", ex.getStatusCode(), ex.getMessage());
            throw new InventoryUnavailableException(
                    "Inventory Service error: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            log.error("Inventory Service unreachable: {}", ex.getMessage());
            throw new InventoryUnavailableException("Inventory Service is unreachable", ex);
        }
    }

    public record Item(Long productId, Integer quantity) {}

    public record AvailabilityResult(boolean allAvailable, List<ItemAvailability> items) {}

    public record ItemAvailability(Long productId, Integer requestedQuantity,
                                   Integer sellableQuantity, boolean available, String reason) {}

    /* ----- private DTOs mirroring the inventory-service contract ----- */

    private record AvailabilityRequestDto(List<Item> items) {}

    @lombok.Data
    @lombok.NoArgsConstructor
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private static class AvailabilityResponseDto {
        private boolean allAvailable;
        private List<ItemAvailability> items;
    }
}
