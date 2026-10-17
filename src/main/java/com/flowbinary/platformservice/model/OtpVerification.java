package com.flowbinary.platformservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A single OTP send/verify transaction for an email address or mobile number.
 * {@code tenant}/{@code user} are nullable — OTPs can be requested before a
 * tenant or user record exists (e.g. signup contact verification).
 * Maps to: otp_verifications
 */
@Entity
@Table(name = "otp_verifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpVerification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private TenantUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private OtpChannel channel;

    /** Email address or E.164 mobile number the code was sent to. */
    @Column(name = "target", nullable = false)
    private String target;

    @Column(name = "purpose", nullable = false)
    @Builder.Default
    private String purpose = "VERIFY_CONTACT";

    /** SHA-256 hash of the OTP code — the plaintext code is never persisted. */
    @Column(name = "otp_hash", nullable = false)
    private String otpHash;

    @Column(name = "verification_token", nullable = false, unique = true)
    private String verificationToken;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private int maxAttempts = 5;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public boolean isMaxAttemptsExceeded() {
        return attemptCount >= maxAttempts;
    }
}
