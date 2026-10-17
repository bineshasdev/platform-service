package com.flowbinary.platformservice.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.LocalDate;
import java.util.List;

/**
 * A diagnostic / pathology / imaging laboratory (tenant type {@code LAB}).
 * Maps to: labs
 */
@Entity
@Table(name = "labs")
@Getter
@Setter
@NoArgsConstructor
public class Lab extends FacilityEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30)
    private LabCategory category;

    @Column(name = "nabl_accredited", nullable = false)
    private boolean nablAccredited = false;

    @Column(name = "nabl_certificate_number", length = 60)
    private String nablCertificateNumber;

    @Column(name = "nabl_valid_till")
    private LocalDate nablValidTill;

    @Column(name = "offers_pathology", nullable = false)
    private boolean offersPathology = false;

    @Column(name = "offers_imaging", nullable = false)
    private boolean offersImaging = false;

    @Column(name = "home_sample_collection", nullable = false)
    private boolean homeSampleCollection = false;

    @Column(name = "home_collection_radius_km")
    private Integer homeCollectionRadiusKm;

    /** Typical report turnaround in hours. */
    @Column(name = "average_turnaround_hours")
    private Integer averageTurnaroundHours;

    /** Delivery channels for reports, e.g. ["EMAIL","SMS","PORTAL","PRINT"]. */
    @Type(JsonBinaryType.class)
    @Column(name = "report_delivery_modes", columnDefinition = "jsonb")
    private List<String> reportDeliveryModes;

    /** Number of tests / panels in the catalogue. */
    @Column(name = "test_catalog_size")
    private Integer testCatalogSize;

    /** Parent lab this branch reports into, when part of a chain. */
    @Column(name = "parent_lab_name")
    private String parentLabName;
}
