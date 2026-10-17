package com.flowbinary.platformservice.dto.registration;

import lombok.Builder;
import lombok.Getter;

/**
 * Outcome of a clinic admin-email verification action (send / resend / verify / change).
 */
@Getter
@Builder
public class EmailVerificationResponse {

    private boolean emailVerified;

    /** The address the code was sent to, partially masked (e.g. {@code d***r@clinic.com}). */
    private String email;

    /** Seconds until the code just sent expires; null when no code was sent. */
    private Long expiresInSeconds;

    private String message;
}
