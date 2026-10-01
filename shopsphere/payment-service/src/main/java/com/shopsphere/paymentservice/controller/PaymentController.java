package com.shopsphere.paymentservice.controller;

import com.shopsphere.paymentservice.dto.PaymentRequest;
import com.shopsphere.paymentservice.dto.PaymentResponse;
import com.shopsphere.paymentservice.exception.ApiError;
import com.shopsphere.paymentservice.service.PaymentService;
import com.shopsphere.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * US46 — locked down: the simulator publishes payment.successful (which ships orders), so it is
 * ADMIN-only; reads are limited to the payment's owner or an ADMIN.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Simulated payment APIs")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/simulate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Simulate a payment (ADMIN)",
            description = """
                    Simulates a payment for an order. NOT a real gateway.
                    `mode`: ALWAYS_SUCCEED, ALWAYS_FAIL, or RANDOM (default, weighted by app.payment.success-rate).
                    Always 201 — inspect `status` for SUCCESSFUL/FAILED.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment record created"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "ADMIN role required")
    })
    public ResponseEntity<PaymentResponse> simulate(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            { "orderId": 1, "userId": 1, "amount": 49.99, "mode": "ALWAYS_SUCCEED" }
                            """))
            )
            @Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.simulate(request);
        return ResponseEntity
                .created(URI.create("/api/v1/payments/" + response.getPaymentReference()))
                .body(response);
    }

    @GetMapping("/{paymentReference}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get a payment by its public reference (owner or ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment found"),
            @ApiResponse(responseCode = "403", description = "Payment belongs to another user"),
            @ApiResponse(responseCode = "404", description = "Payment not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public PaymentResponse getByReference(@PathVariable String paymentReference,
                                          @AuthenticationPrincipal AuthenticatedUser user) {
        PaymentResponse payment = paymentService.getByReference(paymentReference);
        if (!user.isAdmin() && !payment.getUserId().equals(user.userId())) {
            throw new AccessDeniedException("Payment belongs to another user");
        }
        return payment;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List payment attempts for an order (own payments only, unless ADMIN)")
    public List<PaymentResponse> getByOrderId(@RequestParam @Positive Long orderId,
                                              @AuthenticationPrincipal AuthenticatedUser user) {
        List<PaymentResponse> payments = paymentService.getByOrderId(orderId);
        return user.isAdmin()
                ? payments
                : payments.stream().filter(p -> p.getUserId().equals(user.userId())).toList();
    }
}
