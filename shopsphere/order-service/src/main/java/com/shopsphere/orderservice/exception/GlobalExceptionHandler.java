package com.shopsphere.orderservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({ProductNotFoundException.class, OrderNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), req, null);
    }

    @ExceptionHandler(OrderAccessDeniedException.class)
    public ResponseEntity<ApiError> handleForbidden(OrderAccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), req, null);
    }

    @ExceptionHandler(EmptyCartException.class)
    public ResponseEntity<ApiError> handleEmptyCart(EmptyCartException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), req, null);
    }

    @ExceptionHandler({ProductUnavailableException.class, InsufficientStockException.class})
    public ResponseEntity<ApiError> handleConflict(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), req, null);
    }

    @ExceptionHandler({
            CartUnavailableException.class,
            ProductLookupException.class,
            InventoryUnavailableException.class
    })
    public ResponseEntity<ApiError> handleUpstream(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_GATEWAY, "Bad Gateway", ex.getMessage(), req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        return build(HttpStatus.BAD_REQUEST, "Validation Failed", "Request validation failed", req, fieldErrors);
    }

    /** US33 — the create-order body is now required; missing or unparseable JSON is a client error. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", "Request body is missing or malformed", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", ex.getMessage(), req, null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String error, String message,
                                           HttpServletRequest req, Map<String, String> fieldErrors) {
        ApiError body = ApiError.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(req.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
