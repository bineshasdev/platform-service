package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.domain.DoctorClinicAffiliation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DoctorClinicAffiliationRepository extends JpaRepository<DoctorClinicAffiliation, UUID> {

    List<DoctorClinicAffiliation> findByDoctorId(UUID doctorId);

    List<DoctorClinicAffiliation> findByClinicId(UUID clinicId);

    Optional<DoctorClinicAffiliation> findByDoctorIdAndClinicId(UUID doctorId, UUID clinicId);

    boolean existsByDoctorIdAndClinicId(UUID doctorId, UUID clinicId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE DoctorClinicAffiliation a SET a.primaryClinic = false WHERE a.doctor.id = :doctorId")
    void clearPrimaryForDoctor(@Param("doctorId") UUID doctorId);
}
