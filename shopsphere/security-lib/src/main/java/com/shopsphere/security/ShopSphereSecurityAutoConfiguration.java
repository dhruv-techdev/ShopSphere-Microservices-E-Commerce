package com.shopsphere.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * US42 — drop-in security for resource services. Adding the security-lib dependency gives:
 * stateless JWT authentication (same secret as user-service / gateway), @PreAuthorize support,
 * and JSON 401/403 responses. URL rules stay permissive; access control lives on methods.
 */
@AutoConfiguration(before = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSecurity
@EnableMethodSecurity
public class ShopSphereSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtVerifier shopSphereJwtVerifier(
            @Value("${app.jwt.secret:${JWT_SECRET:change-me-to-a-long-random-secret-of-at-least-256-bits-for-hs256}}")
            String secret) {
        return new JwtVerifier(secret);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain shopSphereSecurityFilterChain(HttpSecurity http, JwtVerifier verifier) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .exceptionHandling(eh -> eh.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new JwtAuthenticationFilter(verifier), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** No local users — stops Boot generating a default password. */
    @Bean
    @ConditionalOnMissingBean(UserDetailsService.class)
    public UserDetailsService shopSphereNoLocalUsers() {
        return username -> {
            throw new UsernameNotFoundException("Authentication is JWT-only");
        };
    }

    @Bean
    public SecurityErrorAdvice shopSphereSecurityErrorAdvice() {
        return new SecurityErrorAdvice();
    }
}
