package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.domain.Doctor;
import com.flowbinary.platformservice.dto.registration.DoctorRegistrationRequest;
import com.flowbinary.platformservice.dto.registration.DoctorRegistrationResponse;
import com.flowbinary.platformservice.event.DoctorRegisteredEvent;
import com.flowbinary.platformservice.messaging.PlatformEventPublisher;
import com.flowbinary.platformservice.model.PrincipalType;
import com.flowbinary.platformservice.model.VerificationStatus;
import com.flowbinary.platformservice.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Practitioner self-signup — {@code POST /api/v1/registration/doctors} (public).
 *
 * <p>Creates a platform-wide {@link Doctor} profile plus a Keycloak realm user
 * (no organisation). The profile is {@link VerificationStatus#PENDING} and
 * unlisted until medical-council credentials are verified. Email and mobile are
 * unverified; the web app blocks sign-in until both are confirmed
 * ({@link DoctorContactVerificationService}).
 *
 * <p>The method is deliberately <em>not</em> {@code @Transactional}: the database work
 * runs in a {@link TransactionTemplate} so a failure at <em>commit</em> time (unique-index
 * race, connection loss) is still inside the {@code try} and rolls the Keycloak user back.
 * Side effects (Kafka event, verification codes) fire only after a successful commit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorRegistrationService {

    private final DoctorRepository                 doctorRepository;
    private final KeycloakUserService              keycloak;
    private final PlatformEventPublisher           eventPublisher;
    private final DoctorContactVerificationService contactVerification;
    private final PlatformTransactionManager       transactionManager;
    private final ApplicationEventPublisher        applicationEvents;

    /** Published inside the registration transaction; handled once it has committed. */
    record Registered(DoctorRegisteredEvent event, boolean sendVerificationCodes) {}

    public DoctorRegistrationResponse register(DoctorRegistrationRequest request) {
        return register(request, true);
    }

    /**
     * @param sendVerificationCodes whether to send the email and SMS codes once the signup
     *                              commits. Bulk imports pass {@code false} so we don't message
     *                              hundreds of doctors at once; they request codes at first sign-in.
     */
    public DoctorRegistrationResponse register(DoctorRegistrationRequest request, boolean sendVerificationCodes) {
        String email   = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String phone   = trimToNull(request.getPhone());
        String council = trimToNull(request.getRegistrationCouncil());
        String number  = trimToNull(request.getRegistrationNumber());

        if (number != null && council == null) {
            throw new IllegalArgumentException("Registration council is required with a registration number");
        }
        if (doctorRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("A doctor is already registered with this email: " + email);
        }
        if (phone != null && doctorRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException("A doctor is already registered with this mobile number");
        }
        if (number != null && doctorRepository
                .existsByRegistrationCouncilIgnoreCaseAndRegistrationNumberIgnoreCase(council, number)) {
            throw new IllegalArgumentException(
                    "This council registration number is already registered. Contact support if it is yours.");
        }

        String keycloakUserId = null;
        try {
            keycloakUserId = keycloak.createUser(email, email,
                    request.getFirstName(), request.getLastName(),
                    request.getPassword(), false);
            keycloak.assignRealmRole(keycloakUserId, PrincipalType.TENANT_MEMBER);

            String authUserId = keycloakUserId;
            DoctorRegistrationResponse response = new TransactionTemplate(transactionManager).execute(
                    tx -> persist(request, authUserId, email, phone, council, number, sendVerificationCodes));

            log.info("Doctor registered: id={} email={} keycloakUserId={}",
                    response.getDoctorId(), email, keycloakUserId);
            return response;

        } catch (DataIntegrityViolationException ex) {
            // Lost a race with a concurrent signup (or an older row the pre-checks can't see).
            log.warn("Doctor registration conflicted for email='{}': {}", email, ex.getMostSpecificCause().getMessage());
            keycloak.deleteUserQuietly(keycloakUserId);
            throw new IllegalArgumentException(
                    "A doctor is already registered with this email, mobile number or council registration");
        } catch (IllegalArgumentException ex) {
            // Expected user error (e.g. Keycloak 409) — not an incident.
            log.warn("Doctor registration rejected for email='{}': {}", email, ex.getMessage());
            keycloak.deleteUserQuietly(keycloakUserId);
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Doctor registration failed for email='{}': {}", email, ex.getMessage(), ex);
            keycloak.deleteUserQuietly(keycloakUserId);
            throw ex;
        }
    }

    private DoctorRegistrationResponse persist(DoctorRegistrationRequest request, String keycloakUserId,
                                               String email, String phone, String council, String number,
                                               boolean sendVerificationCodes) {
        Doctor doctor = new Doctor();
        doctor.setAuthUserId(UUID.fromString(keycloakUserId));
        doctor.setFirstName(request.getFirstName().trim());
        doctor.setLastName(request.getLastName().trim());
        doctor.setEmail(email);
        doctor.setPhone(phone);
        doctor.setGender(request.getGender());
        doctor.setPrimarySpecialty(request.getPrimarySpecialty());
        doctor.setRegistrationNumber(number);
        doctor.setRegistrationCouncil(council);
        doctor.setCity(request.getCity());
        doctor.setCountry(request.getCountry());
        doctor.setVerificationStatus(VerificationStatus.PENDING);
        doctor.setListed(false);
        // saveAndFlush: surface unique-index violations here, not at some later flush.
        doctor = doctorRepository.saveAndFlush(doctor);

        applicationEvents.publishEvent(new Registered(
                DoctorRegisteredEvent.builder()
                        .doctorId(doctor.getId())
                        .authUserId(doctor.getAuthUserId())
                        .email(doctor.getEmail())
                        .firstName(doctor.getFirstName())
                        .lastName(doctor.getLastName())
                        .build(),
                sendVerificationCodes));

        return DoctorRegistrationResponse.builder()
                .doctorId(doctor.getId())
                .keycloakUserId(keycloakUserId)
                .firstName(doctor.getFirstName())
                .lastName(doctor.getLastName())
                .email(doctor.getEmail())
                .phone(doctor.getPhone())
                .verificationStatus(doctor.getVerificationStatus())
                .listed(doctor.isListed())
                .createdAt(doctor.getCreatedAt())
                .nextSteps(List.of(
                        "Sign in, then verify your email and mobile number with the codes we send — "
                                + "you can't continue until both are verified",
                        "Add your council registration and qualification documents",
                        "Your profile is listed in search once verified"))
                .build();
    }

    /** Runs only after the signup has committed, so a rolled-back signup never emits anything. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRegistered(Registered registered) {
        try {
            eventPublisher.publishDoctorRegistered(registered.event());
        } catch (RuntimeException ex) {
            log.warn("Could not publish DOCTOR_REGISTERED for doctor {}: {}",
                    registered.event().getDoctorId(), ex.getMessage());
        }
        if (registered.sendVerificationCodes()) {
            contactVerification.sendInitialCodes(registered.event().getDoctorId());
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
