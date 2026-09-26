package com.shopsphere.orderservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Postal address value object, embedded into owning entities.
 *
 * <p>The validation constraints are also enforced by Hibernate before insert/update, as a
 * second line of defence behind request validation. DB columns stay nullable so orders
 * created before US33 (no address) remain valid.</p>
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class Address {

    @NotBlank
    @Size(max = 100)
    @Column(name = "recipient_name", length = 100)
    private String recipientName;

    @Size(max = 20)
    @Column(name = "phone", length = 20)
    private String phone;

    @NotBlank
    @Size(max = 200)
    @Column(name = "line1", length = 200)
    private String line1;

    @Size(max = 200)
    @Column(name = "line2", length = 200)
    private String line2;

    @NotBlank
    @Size(max = 100)
    @Column(name = "city", length = 100)
    private String city;

    @Size(max = 100)
    @Column(name = "state", length = 100)
    private String state;

    @NotBlank
    @Size(max = 20)
    @Column(name = "postal_code", length = 20)
    private String postalCode;

    /** ISO 3166-1 alpha-2, always stored upper case. */
    @NotBlank
    @Pattern(regexp = "^[A-Z]{2}$")
    @Column(name = "country", length = 2)
    private String country;
}
