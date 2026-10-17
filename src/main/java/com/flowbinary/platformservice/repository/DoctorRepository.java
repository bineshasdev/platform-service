package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.domain.Doctor;
import com.flowbinary.platformservice.model.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, UUID> {

    Optional<Doctor> findByAuthUserId(UUID authUserId);
    Optional<Doctor> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPhone(String phone);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);
    boolean existsByPhoneAndIdNot(String phone, UUID id);
    boolean existsByRegistrationCouncilIgnoreCaseAndRegistrationNumberIgnoreCase(
            String registrationCouncil, String registrationNumber);

    List<Doctor> findByVerificationStatus(VerificationStatus verificationStatus);
    List<Doctor> findByListedTrue();
}
