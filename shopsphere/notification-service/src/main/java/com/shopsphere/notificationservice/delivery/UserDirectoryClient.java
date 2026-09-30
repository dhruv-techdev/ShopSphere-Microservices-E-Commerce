package com.shopsphere.notificationservice.delivery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/** US39 — resolves userId → email via user-service's internal endpoint. */
@Component
public class UserDirectoryClient {

    static final String TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;

    public UserDirectoryClient(RestClient.Builder builder,
                               @Value("${app.user-service.base-url}") String baseUrl,
                               @Value("${app.internal.token:}") String internalToken) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(TOKEN_HEADER, internalToken)
                .build();
    }

    /** Empty if the user doesn't exist. Other failures (5xx, timeouts, 401) propagate. */
    public Optional<UserContact> findContact(Long userId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/internal/v1/users/{id}/contact", userId)
                    .retrieve()
                    .body(UserContact.class));
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        }
    }

    public record UserContact(Long id, String email, String firstName, Boolean enabled) {
    }
}
