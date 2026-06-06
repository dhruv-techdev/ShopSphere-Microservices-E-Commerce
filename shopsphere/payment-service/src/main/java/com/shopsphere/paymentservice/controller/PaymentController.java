package com.shopsphere.paymentservice.controller;

import com.shopsphere.paymentservice.dto.PaymentRequest;
import com.shopsphere.paymentservice.dto.PaymentResponse;
import com.shopsphere.paymentservice.exception.ApiError;
import com.shopsphere.paymentservice.service.PaymentService;
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
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Simulated payment APIs")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/simulate")
    @Operation(
            summary = "Simulate a payment",
            description = """
                    Simulates a payment for an order. NOT a real gateway.

                    Flow:
                    1. A PENDING payment record is persisted.
                    2. A simulated gateway latency elapses.
                    3. Outcome decided:
                       - `mode=ALWAYS_SUCCEED` → SUCCESSFUL
                       - `mode=ALWAYS_FAIL` → FAILED
                       - `mode=RANDOM` or omitted → weighted by `app.payment.success-rate`
                    4. Status updated and final result returned.

                    The HTTP response is always `201 Created` — failures are *business* failures
                    represented in the body, not transport failures. Inspect `status` and `message`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Payment record created. Inspect `status` for SUCCESSFUL/FAILED."),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<PaymentResponse> simulate(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(examples = {
                            @ExampleObject(name = "Random outcome", value = """
                                    {
                                      "orderId": 1,
                                      "userId": 1,
                                      "amount": 49.99
                                    }
                                    """),
                            @ExampleObject(name = "Force success", value = """
                                    {
                                      "orderId": 1,
                                      "userId": 1,
                                      "amount": 49.99,
                                      "mode": "ALWAYS_SUCCEED"
                                    }
                                    """),
                            @ExampleObject(name = "Force failure", value = """
                                    {
                                      "orderId": 1,
                                      "userId": 1,
                                      "amount": 49.99,
                                      "mode": "ALWAYS_FAIL"
                                    }
                                    """)
                    })
            )
            @Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.simulate(request);
        return ResponseEntity
                .created(URI.create("/api/v1/payments/" + response.getPaymentReference()))
                .body(response);
    }

    @GetMapping("/{paymentReference}")
    @Operation(summary = "Get a payment by its public reference (UUID)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment found"),
            @ApiResponse(responseCode = "404", description = "Payment not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public PaymentResponse getByReference(@PathVariable String paymentReference) {
        return paymentService.getByReference(paymentReference);
    }

    @GetMapping
    @Operation(
            summary = "List all payment attempts for an order",
            description = "Returns all payments for an order, newest first. Useful for retry audit trails."
    )
    public List<PaymentResponse> getByOrderId(
            @RequestParam @Positive Long orderId) {
        return paymentService.getByOrderId(orderId);
    }
}
