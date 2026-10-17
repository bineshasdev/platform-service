package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.domain.Patient;
import com.flowbinary.platformservice.dto.registration.PatientRegistrationRequest;
import com.flowbinary.platformservice.dto.registration.PatientRegistrationResponse;
import com.flowbinary.platformservice.event.PatientRegisteredEvent;
import com.flowbinary.platformservice.messaging.PlatformEventPublisher;
import com.flowbinary.platformservice.model.PlatformRole;
import com.flowbinary.platformservice.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Patient self-signup — {@code POST /api/v1/registration/patients} (public).
 *
 * <p>Creates a platform-global {@link Patient} account plus a Keycloak realm
 * user (no organisation). No credential verification; email / phone are marked
 * unverified and can be confirmed later via the OTP endpoints.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatientRegistrationService {

    private final PatientRepository       patientRepository;
    private final KeycloakUserService     keycloak;
    private final PlatformEventPublisher  eventPublisher;

    @Transactional
    public PatientRegistrationResponse register(PatientRegistrationRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (patientRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("An account already exists with this email: " + email);
        }
        if (request.getPhone() != null && patientRepository.existsByPhone(request.getPhone())) {
            throw new IllegalArgumentException("An account already exists with this mobile number");
        }

        String keycloakUserId = null;
        try {
            keycloakUserId = keycloak.createUser(email, email,
                    request.getFirstName(), request.getLastName(),
                    request.getPassword(), false);
            keycloak.assignRealmRole(keycloakUserId, PlatformRole.PATIENT);

            Patient patient = new Patient();
            patient.setAuthUserId(UUID.fromString(keycloakUserId));
            patient.setFirstName(request.getFirstName());
            patient.setLastName(request.getLastName());
            patient.setEmail(email);
            patient.setPhone(request.getPhone());
            patient.setGender(request.getGender());
            patient.setDateOfBirth(request.getDateOfBirth());
            patient.setPreferredLanguage(request.getPreferredLanguage());
            patient.setMarketingOptIn(request.isMarketingOptIn());
            patient = patientRepository.save(patient);

            eventPublisher.publishPatientRegistered(PatientRegisteredEvent.builder()
                    .patientId(patient.getId())
                    .authUserId(patient.getAuthUserId())
                    .email(patient.getEmail())
                    .firstName(patient.getFirstName())
                    .lastName(patient.getLastName())
                    .build());

            log.info("Patient registered: id={} email={} keycloakUserId={}",
                    patient.getId(), email, keycloakUserId);

            return PatientRegistrationResponse.builder()
                    .patientId(patient.getId())
                    .keycloakUserId(keycloakUserId)
                    .firstName(patient.getFirstName())
                    .lastName(patient.getLastName())
                    .email(patient.getEmail())
                    .phone(patient.getPhone())
                    .emailVerified(patient.isEmailVerified())
                    .phoneVerified(patient.isPhoneVerified())
                    .createdAt(patient.getCreatedAt())
                    .build();

        } catch (Exception ex) {
            log.error("Patient registration failed for email='{}': {}", email, ex.getMessage(), ex);
            keycloak.deleteUserQuietly(keycloakUserId);
            throw ex;
        }
    }
}
