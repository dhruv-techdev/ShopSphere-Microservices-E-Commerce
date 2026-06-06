package com.shopsphere.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.security")
public class SecurityPaths {
    private List<String> publicPaths = new ArrayList<>();
    private List<String> publicGetPrefixes = new ArrayList<>();
    private List<String> adminWritePrefixes = new ArrayList<>();

    public boolean isPublic(String path, String method) {
        if (publicPaths.contains(path)) return true;
        if ("GET".equalsIgnoreCase(method)) {
            return publicGetPrefixes.stream().anyMatch(path::startsWith);
        }
        return false;
    }

    public boolean requiresAdmin(String path, String method) {
        if ("GET".equalsIgnoreCase(method)) return false;
        return adminWritePrefixes.stream().anyMatch(path::startsWith);
    }
}
