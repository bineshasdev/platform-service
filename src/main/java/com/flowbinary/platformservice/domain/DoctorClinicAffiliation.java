package com.flowbinary.platformservice.domain;

import com.flowbinary.platformservice.model.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Many-to-many link between a {@link Doctor} and a {@link Clinic} — a doctor may
 * practise at several clinics, a clinic has many doctors.
 * Maps to: doctor_clinic_affiliations
 */
@Entity
@Table(name = "doctor_clinic_affiliations",
        uniqueConstraints = @UniqueConstraint(name = "uq_doctor_clinic",
                columnNames = {"doctor_id", "clinic_id"}))
@Getter
@Setter
@NoArgsConstructor
public class DoctorClinicAffiliation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @Enumerated(EnumType.STRING)
    @Column(name = "affiliation_type", length = 20)
    private AffiliationType affiliationType;

    /** Title held at this clinic, e.g. "Senior Consultant - Cardiology". */
    @Column(name = "designation")
    private String designation;

    @Column(name = "department", length = 120)
    private String department;

    @Column(name = "consultation_fee", precision = 10, scale = 2)
    private BigDecimal consultationFee;

    @Column(name = "currency", length = 3)
    private String currency;

    /** The doctor's primary place of practice. At most one per doctor (enforced in service). */
    @Column(name = "primary_clinic", nullable = false)
    private boolean primaryClinic = false;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** OPD hours at this clinic, same shape as {@code FacilityEntity.operatingHours}. */
    @Type(JsonBinaryType.class)
    @Column(name = "schedule", columnDefinition = "jsonb")
    private Map<String, Object> schedule;
}
