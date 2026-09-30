package com.shopsphere.notificationservice.delivery;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RecipientResolver {

    private final UserDirectoryClient userDirectory;
    private final NotificationProperties properties;

    /** null userId → admin address (system alerts). Disabled users / missing emails → empty. */
    public Optional<Recipient> resolve(Long userId) {
        if (userId == null) {
            String admin = properties.getAdminEmail();
            return admin == null || admin.isBlank()
                    ? Optional.empty()
                    : Optional.of(new Recipient(admin, "ShopSphere team"));
        }
        return userDirectory.findContact(userId)
                .filter(c -> !Boolean.FALSE.equals(c.enabled()))
                .filter(c -> c.email() != null && !c.email().isBlank())
                .map(c -> new Recipient(c.email(),
                        c.firstName() == null || c.firstName().isBlank() ? "there" : c.firstName()));
    }

    public record Recipient(String email, String displayName) {
    }
}
