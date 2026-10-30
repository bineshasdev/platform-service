package com.flowbinary.platformservice.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A clinic / polyclinic facility (tenant type {@code CLINIC}).
 * Maps to: clinics
 */
@Entity
@Table(name = "clinics")
@Getter
@Setter
@NoArgsConstructor
public class Clinic extends FacilityEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30)
    private ClinicCategory category;

    /**
     * Medical specialties offered, e.g. ["CARDIOLOGY","DERMATOLOGY"]. Defaulted empty
     * (not left null) because the column is {@code NOT NULL DEFAULT '[]'} — clinic
     * self-signup doesn't require specialties up front, and an explicit Java
     * {@code null} would send SQL {@code NULL}, which bypasses the column default
     * and trips the NOT NULL constraint.
     */
    @Type(JsonBinaryType.class)
    @Column(name = "specialties", columnDefinition = "jsonb")
    private List<String> specialties = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "appointment_mode", length = 20)
    private AppointmentMode appointmentMode;

    @Column(name = "teleconsultation_enabled", nullable = false)
    private boolean teleconsultationEnabled = false;

    @Column(name = "default_consultation_fee", precision = 10, scale = 2)
    private BigDecimal defaultConsultationFee;

    /** ISO 4217 currency for fees; falls back to the tenant currency when null. */
    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "practitioner_count")
    private Integer practitionerCount;

    /** Observation / day-care beds (0 for pure OPD clinics). */
    @Column(name = "bed_count")
    private Integer bedCount;

    @Column(name = "has_in_house_pharmacy", nullable = false)
    private boolean hasInHousePharmacy = false;

    @Column(name = "has_in_house_lab", nullable = false)
    private boolean hasInHouseLab = false;

    @Column(name = "emergency_services", nullable = false)
    private boolean emergencyServices = false;
}
