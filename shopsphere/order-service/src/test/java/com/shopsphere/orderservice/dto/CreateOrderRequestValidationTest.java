package com.shopsphere.orderservice.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CreateOrderRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static AddressDto.AddressDtoBuilder validAddress() {
        return AddressDto.builder()
                .recipientName("Jane Doe")
                .line1("123 King St W")
                .city("Toronto")
                .state("ON")
                .postalCode("M5V 3L9")
                .country("CA");
    }

    private Set<String> violatedPaths(CreateOrderRequest request) {
        Set<ConstraintViolation<CreateOrderRequest>> violations = validator.validate(request);
        return violations.stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void validRequest_hasNoViolations() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .shippingAddress(validAddress().build())
                .build();

        assertThat(violatedPaths(request)).isEmpty();
    }

    @Test
    void missingShippingAddress_isRejected() {
        assertThat(violatedPaths(new CreateOrderRequest())).containsExactly("shippingAddress");
    }

    @Test
    void missingRequiredAddressFields_areRejected() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .shippingAddress(AddressDto.builder().build())
                .build();

        assertThat(violatedPaths(request)).containsExactlyInAnyOrder(
                "shippingAddress.recipientName",
                "shippingAddress.line1",
                "shippingAddress.city",
                "shippingAddress.postalCode",
                "shippingAddress.country");
    }

    @Test
    void invalidCountryCode_isRejected() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .shippingAddress(validAddress().country("CAN").build())
                .build();

        assertThat(violatedPaths(request)).containsExactly("shippingAddress.country");
    }

    @Test
    void invalidPostalCode_isRejected() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .shippingAddress(validAddress().postalCode("M5V#3L9").build())
                .build();

        assertThat(violatedPaths(request)).containsExactly("shippingAddress.postalCode");
    }

    @Test
    void invalidPhone_isRejected() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .shippingAddress(validAddress().phone("call me").build())
                .build();

        assertThat(violatedPaths(request)).containsExactly("shippingAddress.phone");
    }

    @Test
    void tooLongLine1_isRejected() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .shippingAddress(validAddress().line1("x".repeat(201)).build())
                .build();

        assertThat(violatedPaths(request)).containsExactly("shippingAddress.line1");
    }
}
