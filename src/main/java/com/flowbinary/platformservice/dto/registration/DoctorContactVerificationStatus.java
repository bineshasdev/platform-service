package com.flowbinary.platformservice.dto.registration;

import lombok.Builder;
import lombok.Getter;

/**
 * Whether a signed-in practitioner has proven ownership of their email and mobile.
 * Sign-in is blocked on the web app until {@link #verified} is true.
 */
@Getter
@Builder
public class DoctorContactVerificationStatus {

    /** False for users that aren't practitioners — nothing to verify, nothing to block. */
    private boolean required;

    /** True when no further verification is needed (not required, or both channels confirmed). */
    private boolean verified;

    private boolean emailVerified;
    private boolean phoneVerified;

    /** Partially masked, e.g. {@code d***r@clinic.com}. */
    private String email;

    /** Partially masked, e.g. {@code ***5678}; null when no mobile is on file. */
    private String phone;

    /** Seconds until a code just sent expires; null when no code was sent. */
    private Long expiresInSeconds;

    private String message;
}
