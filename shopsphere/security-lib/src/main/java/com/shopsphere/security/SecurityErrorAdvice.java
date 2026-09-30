package com.shopsphere.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @PreAuthorize failures surface inside the DispatcherServlet, where each service's
 * catch-all @ExceptionHandler(Exception.class) would turn them into 500s. This advice runs
 * first and maps them properly: not logged in → 401, logged in without the role → 403.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityErrorAdvice {

    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public ResponseEntity<Map<String, Object>> handle(RuntimeException ex, HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);

        HttpStatus status = (ex instanceof AuthenticationException || !authenticated)
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.FORBIDDEN;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", status == HttpStatus.UNAUTHORIZED
                ? "Authentication required"
                : "You don't have permission to perform this action");
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
