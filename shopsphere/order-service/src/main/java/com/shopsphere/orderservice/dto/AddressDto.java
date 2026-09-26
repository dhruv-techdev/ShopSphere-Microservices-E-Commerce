package com.shopsphere.orderservice.dto;

import com.shopsphere.orderservice.entity.Address;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Locale;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Postal address")
public class AddressDto {

    @Schema(example = "Jane Doe")
    @NotBlank(message = "Recipient name is required")
    @Size(max = 100, message = "Recipient name must be at most 100 characters")
    private String recipientName;

    @Schema(example = "+1 416 555 0199")
    @Size(max = 20, message = "Phone must be at most 20 characters")
    @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$", message = "Phone must contain only digits, spaces, (), - and an optional leading +")
    private String phone;

    @Schema(example = "123 King St W")
    @NotBlank(message = "Address line 1 is required")
    @Size(max = 200, message = "Address line 1 must be at most 200 characters")
    private String line1;

    @Schema(example = "Unit 4")
    @Size(max = 200, message = "Address line 2 must be at most 200 characters")
    private String line2;

    @Schema(example = "Toronto")
    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City must be at most 100 characters")
    private String city;

    @Schema(example = "ON")
    @Size(max = 100, message = "State/province must be at most 100 characters")
    private String state;

    @Schema(example = "M5V 3L9")
    @NotBlank(message = "Postal code is required")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 -]{1,18}[A-Za-z0-9]$",
            message = "Postal code must be 3-20 letters, digits, spaces or hyphens")
    private String postalCode;

    @Schema(example = "CA", description = "ISO 3166-1 alpha-2 country code")
    @NotBlank(message = "Country is required")
    @Pattern(regexp = "^[A-Za-z]{2}$", message = "Country must be a 2-letter ISO 3166-1 alpha-2 code")
    private String country;

    /** Maps to the embeddable, trimming input and normalising case. */
    public Address toEntity() {
        return Address.builder()
                .recipientName(trimToNull(recipientName))
                .phone(trimToNull(phone))
                .line1(trimToNull(line1))
                .line2(trimToNull(line2))
                .city(trimToNull(city))
                .state(trimToNull(state))
                .postalCode(upper(trimToNull(postalCode)))
                .country(upper(trimToNull(country)))
                .build();
    }

    public static AddressDto from(Address address) {
        if (address == null) {
            return null;
        }
        return AddressDto.builder()
                .recipientName(address.getRecipientName())
                .phone(address.getPhone())
                .line1(address.getLine1())
                .line2(address.getLine2())
                .city(address.getCity())
                .state(address.getState())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .build();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}
