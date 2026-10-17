package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.domain.Doctor;
import com.flowbinary.platformservice.dto.otp.SendOtpResponse;
import com.flowbinary.platformservice.dto.registration.DoctorContactVerificationStatus;
import com.flowbinary.platformservice.exception.InvalidOtpException;
import com.flowbinary.platformservice.exception.OtpAttemptsExceededException;
import com.flowbinary.platformservice.exception.OtpExpiredException;
import com.flowbinary.platformservice.model.OtpChannel;
import com.flowbinary.platformservice.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Email and mobile ownership checks for practitioners, enforced at sign-in.
 *
 * <p>Everything is keyed on the caller's Keycloak user id (the token {@code sub}), never a
 * client-supplied doctor id, so a user can only act on their own profile. A user with no
 * doctor profile is reported as {@code required=false} rather than an error, which lets
 * the web app ask this question of every signed-in user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorContactVerificationService {

    static final String EMAIL_PURPOSE = "DOCTOR_EMAIL_VERIFY";
    static final String PHONE_PURPOSE = "DOCTOR_PHONE_VERIFY";

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern E164  = Pattern.compile("^\\+[1-9]\\d{6,14}$");

    public enum Channel {
        EMAIL(OtpChannel.EMAIL, EMAIL_PURPOSE),
        PHONE(OtpChannel.SMS, PHONE_PURPOSE);

        final OtpChannel otpChannel;
        final String purpose;

        Channel(OtpChannel otpChannel, String purpose) {
            this.otpChannel = otpChannel;
            this.purpose = purpose;
        }

        public static Channel parse(String raw) {
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Unknown channel '" + raw + "' — use 'email' or 'phone'");
            }
        }
    }

    private final DoctorRepository    doctorRepository;
    private final OtpService          otpService;
    private final KeycloakUserService keycloak;

    @Transactional(readOnly = true)
    public DoctorContactVerificationStatus status(UUID authUserId) {
        return doctorRepository.findByAuthUserId(authUserId)
                .map(d -> toStatus(d, null, null))
                .orElseGet(() -> DoctorContactVerificationStatus.builder()
                        .required(false).verified(true).emailVerified(true).phoneVerified(true).build());
    }

    /**
     * Sends the first code on each channel after signup. Failures are only logged — the
     * practitioner can request codes from the verification page.
     */
    public void sendInitialCodes(UUID doctorId) {
        Doctor doctor = doctorRepository.findById(doctorId).orElse(null);
        if (doctor == null) return;
        for (Channel channel : Channel.values()) {
            try {
                sendCode(doctor, channel);
            } catch (RuntimeException ex) {
                log.warn("Could not send initial {} code for doctor {}: {}", channel, doctorId, ex.getMessage());
            }
        }
    }

    @Transactional
    public DoctorContactVerificationStatus send(UUID authUserId, Channel channel) {
        Doctor doctor = getDoctor(authUserId);
        if (isVerified(doctor, channel)) {
            return toStatus(doctor, null, "Already verified");
        }
        SendOtpResponse sent = sendCode(doctor, channel);
        return toStatus(doctor, sent.getExpiresInSeconds(), "Verification code sent");
    }

    @Transactional(noRollbackFor = {
            InvalidOtpException.class, OtpExpiredException.class, OtpAttemptsExceededException.class})
    public DoctorContactVerificationStatus verify(UUID authUserId, Channel channel, String otp) {
        Doctor doctor = getDoctor(authUserId);
        if (isVerified(doctor, channel)) {
            return toStatus(doctor, null, "Already verified");
        }

        otpService.verifyLatest(channel.otpChannel, target(doctor, channel), channel.purpose, otp);

        if (channel == Channel.EMAIL) {
            doctor.setEmailVerified(true);
        } else {
            doctor.setPhoneVerified(true);
        }
        doctorRepository.save(doctor);

        if (channel == Channel.EMAIL) {
            try {
                keycloak.setEmailVerified(authUserId.toString(), true);
            } catch (RuntimeException ex) {
                // The platform record is what gates sign-in; don't undo it for a Keycloak blip.
                log.warn("Doctor {} email verified but Keycloak flag not updated: {}", doctor.getId(), ex.getMessage());
            }
        }

        log.info("Doctor {} verified: doctor={}", channel, doctor.getId());
        return toStatus(doctor, null, channel == Channel.EMAIL ? "Email verified" : "Mobile number verified");
    }

    /**
     * Corrects a mistyped email or mobile number (also the way to fix an imported number that
     * isn't E.164). Only while that channel is unverified; a fresh code goes to the new value.
     */
    @Transactional
    public DoctorContactVerificationStatus change(UUID authUserId, Channel channel, String rawValue) {
        Doctor doctor = getDoctor(authUserId);
        if (isVerified(doctor, channel)) {
            throw new IllegalArgumentException("This contact is already verified and can't be changed here");
        }
        String value = rawValue.trim();

        if (channel == Channel.EMAIL) {
            value = value.toLowerCase(Locale.ROOT);
            if (!EMAIL.matcher(value).matches()) {
                throw new IllegalArgumentException("Invalid email address");
            }
            if (!value.equalsIgnoreCase(doctor.getEmail())) {
                if (doctorRepository.existsByEmailIgnoreCaseAndIdNot(value, doctor.getId())) {
                    throw new IllegalArgumentException("A doctor is already registered with this email: " + value);
                }
                doctor.setEmail(value);
                doctorRepository.saveAndFlush(doctor);
                keycloak.updateEmail(authUserId.toString(), value);
            }
        } else {
            if (!E164.matcher(value).matches()) {
                throw new IllegalArgumentException("Mobile must be in E.164 format, e.g. +919812345678");
            }
            if (!value.equals(doctor.getPhone())) {
                if (doctorRepository.existsByPhoneAndIdNot(value, doctor.getId())) {
                    throw new IllegalArgumentException("A doctor is already registered with this mobile number");
                }
                doctor.setPhone(value);
                doctorRepository.saveAndFlush(doctor);
            }
        }

        try {
            SendOtpResponse sent = sendCode(doctor, channel);
            return toStatus(doctor, sent.getExpiresInSeconds(), "Updated — verification code sent");
        } catch (RuntimeException ex) {
            log.warn("Doctor {} {} updated but code not sent: {}", doctor.getId(), channel, ex.getMessage());
            return toStatus(doctor, null, "Updated — we couldn't send a code yet, please use resend");
        }
    }

    // ── internals ─────────────────────────────────────────────────────────

    private SendOtpResponse sendCode(Doctor doctor, Channel channel) {
        return otpService.sendOtp(channel.otpChannel, target(doctor, channel), channel.purpose, null, null);
    }

    private static String target(Doctor doctor, Channel channel) {
        String target = channel == Channel.EMAIL ? doctor.getEmail() : doctor.getPhone();
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("No mobile number on file — add one first");
        }
        return target;
    }

    private static boolean isVerified(Doctor doctor, Channel channel) {
        return channel == Channel.EMAIL ? doctor.isEmailVerified() : doctor.isPhoneVerified();
    }

    private Doctor getDoctor(UUID authUserId) {
        return doctorRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new NoSuchElementException("No practitioner profile for this user"));
    }

    private static DoctorContactVerificationStatus toStatus(Doctor doctor, Long expiresInSeconds, String message) {
        return DoctorContactVerificationStatus.builder()
                .required(true)
                .verified(doctor.isEmailVerified() && doctor.isPhoneVerified())
                .emailVerified(doctor.isEmailVerified())
                .phoneVerified(doctor.isPhoneVerified())
                .email(maskEmail(doctor.getEmail()))
                .phone(maskPhone(doctor.getPhone()))
                .expiresInSeconds(expiresInSeconds)
                .message(message)
                .build();
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at < 0) return email;
        String local = email.substring(0, at);
        String masked = local.length() <= 2 ? local : local.charAt(0) + "***" + local.charAt(local.length() - 1);
        return masked + email.substring(at);
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        return phone.length() <= 4 ? phone : "***" + phone.substring(phone.length() - 4);
    }
}
