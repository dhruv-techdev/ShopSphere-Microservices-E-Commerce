package com.shopsphere.security.testapp;

import com.shopsphere.security.AuthenticatedUser;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Minimal app that exercises the auto-configuration exactly as a service would. */
@SpringBootApplication
public class SecurityProbeApplication {

    @RestController
    static class ProbeController {

        @GetMapping("/public")
        String open() {
            return "ok";
        }

        @PreAuthorize("hasRole('ADMIN')")
        @PostMapping("/admin")
        String adminOnly() {
            return "admin";
        }

        @PreAuthorize("isAuthenticated()")
        @GetMapping("/me")
        String me(@AuthenticationPrincipal AuthenticatedUser user) {
            return user.userId() + ":" + user.email() + ":" + user.role();
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("unrelated failure");
        }
    }
}
