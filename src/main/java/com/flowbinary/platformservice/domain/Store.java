package com.flowbinary.platformservice.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A pharmacy / medical store (tenant type {@code STORE}).
 * Maps to: stores
 */
@Entity
@Table(name = "stores")
@Getter
@Setter
@NoArgsConstructor
public class Store extends FacilityEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30)
    private StoreCategory category;

    // ── Regulatory ────────────────────────────────────────────────────────

    @Column(name = "drug_license_number", length = 120)
    private String drugLicenseNumber;

    @Column(name = "drug_license_valid_till")
    private LocalDate drugLicenseValidTill;

    @Column(name = "gstin", length = 20)
    private String gstin;

    @Column(name = "pharmacist_in_charge_name")
    private String pharmacistInChargeName;

    @Column(name = "pharmacist_registration_number", length = 60)
    private String pharmacistRegistrationNumber;

    @Column(name = "stocks_controlled_substances", nullable = false)
    private boolean stocksControlledSubstances = false;

    // ── Commerce ──────────────────────────────────────────────────────────

    @Column(name = "open_24x7", nullable = false)
    private boolean open24x7 = false;

    @Column(name = "online_ordering_enabled", nullable = false)
    private boolean onlineOrderingEnabled = false;

    @Column(name = "delivery_available", nullable = false)
    private boolean deliveryAvailable = false;

    @Column(name = "delivery_radius_km")
    private Integer deliveryRadiusKm;

    @Column(name = "min_order_value", precision = 10, scale = 2)
    private BigDecimal minOrderValue;

    @Column(name = "accepts_insurance", nullable = false)
    private boolean acceptsInsurance = false;
}
