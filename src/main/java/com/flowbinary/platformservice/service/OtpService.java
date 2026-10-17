package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.dto.otp.SendOtpRequest;
import com.flowbinary.platformservice.dto.otp.SendOtpResponse;
import com.flowbinary.platformservice.dto.otp.VerifyOtpRequest;
import com.flowbinary.platformservice.dto.otp.VerifyOtpResponse;
import com.flowbinary.platformservice.exception.InvalidOtpException;
import com.flowbinary.platformservice.exception.OtpAttemptsExceededException;
import com.flowbinary.platformservice.exception.OtpExpiredException;
import com.flowbinary.platformservice.exception.OtpRateLimitExceededException;
import com.flowbinary.platformservice.model.OtpChannel;
import com.flowbinary.platformservice.model.OtpVerification;
import com.flowbinary.platformservice.model.Tenant;
import com.flowbinary.platformservice.model.TenantUser;
import com.flowbinary.platformservice.repository.OtpVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final int OTP_VALIDITY_MINUTES = 15;
    private static final int MAX_ATTEMPTS = 5;
    private static final int RATE_LIMIT_WINDOW_MINUTES = 15;
    private static final int RATE_LIMIT_MAX_REQUESTS = 3;
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    /** Superseded/expired rows are kept this long so they keep counting towards the rate limit. */
    private static final Duration EXPIRED_RETENTION = Duration.ofHours(1);

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern E164_PATTERN =
            Pattern.compile("^\\+[1-9]\\d{6,14}$");

    private final OtpVerificationRepository otpVerificationRepository;
    private final NotificationServiceClient notificationServiceClient;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public SendOtpResponse sendOtp(SendOtpRequest request, Tenant tenant, TenantUser user) {
        String purpose = request.getPurpose() != null ? request.getPurpose() : "VERIFY_CONTACT";
        return sendOtp(request.getChannel(), request.getTarget(), purpose, tenant, user);
    }

    @Transactional
    public SendOtpResponse sendOtp(OtpChannel channel, String rawTarget, String purpose,
                                   Tenant tenant, TenantUser user) {
        String target = normalizeTarget(channel, rawTarget);

        enforceRateLimit(target, channel);

        otpVerificationRepository.expireUnverifiedByTargetAndChannel(target, channel, Instant.now());

        String otp = generateOtp();
        Instant expiresAt = Instant.now().plus(OTP_VALIDITY_MINUTES, ChronoUnit.MINUTES);

        OtpVerification otpVerification = OtpVerification.builder()
                .tenant(tenant)
                .user(user)
                .channel(channel)
                .target(target)
                .purpose(purpose)
                .otpHash(hash(otp))
                .verificationToken(UUID.randomUUID().toString())
                .maxAttempts(MAX_ATTEMPTS)
                .expiresAt(expiresAt)
                .build();

        otpVerification = otpVerificationRepository.save(otpVerification);

        notificationServiceClient.sendOtp(channel, target, otp, purpose, OTP_VALIDITY_MINUTES);

        log.info("OTP sent: channel={} target={} purpose={}", channel, maskTarget(target), purpose);

        return SendOtpResponse.builder()
                .verificationToken(otpVerification.getVerificationToken())
                .expiresInSeconds(Duration.between(Instant.now(), expiresAt).getSeconds())
                .message("Verification code sent")
                .build();
    }

    @Transactional(noRollbackFor = {
            InvalidOtpException.class, OtpExpiredException.class, OtpAttemptsExceededException.class})
    public VerifyOtpResponse verifyOtp(VerifyOtpRequest request) {
        OtpVerification otpVerification = otpVerificationRepository
                .findByVerificationTokenAndVerifiedAtIsNull(request.getVerificationToken())
                .orElseThrow(() -> new InvalidOtpException("Invalid or already-used verification token"));
        return confirm(otpVerification, request.getOtp());
    }

    /**
     * Verifies the latest outstanding code sent to {@code target} for {@code purpose}.
     * Lets callers that triggered the send server-side verify without ever handling
     * the verification token.
     */
    @Transactional(noRollbackFor = {
            InvalidOtpException.class, OtpExpiredException.class, OtpAttemptsExceededException.class})
    public VerifyOtpResponse verifyLatest(OtpChannel channel, String rawTarget, String purpose, String otp) {
        String target = normalizeTarget(channel, rawTarget);
        OtpVerification otpVerification = otpVerificationRepository
                .findFirstByTargetAndChannelAndPurposeAndVerifiedAtIsNullOrderByCreatedAtDesc(target, channel, purpose)
                .orElseThrow(() -> new InvalidOtpException(
                        "No pending verification code. Please request a new one."));
        return confirm(otpVerification, otp);
    }

    private VerifyOtpResponse confirm(OtpVerification otpVerification, String otp) {
        if (otpVerification.isExpired()) {
            throw new OtpExpiredException("Verification code has expired. Please request a new one.");
        }

        if (otpVerification.isMaxAttemptsExceeded()) {
            throw new OtpAttemptsExceededException(
                    "Maximum verification attempts exceeded. Please request a new code.");
        }

        otpVerification.setAttemptCount(otpVerification.getAttemptCount() + 1);

        if (!hash(otp).equals(otpVerification.getOtpHash())) {
            otpVerificationRepository.save(otpVerification);
            int attemptsLeft = otpVerification.getMaxAttempts() - otpVerification.getAttemptCount();
            throw new InvalidOtpException(attemptsLeft > 0
                    ? "Incorrect code. " + attemptsLeft + " attempt(s) remaining."
                    : "Incorrect code. Maximum attempts exceeded. Please request a new code.");
        }

        otpVerification.setVerifiedAt(Instant.now());
        otpVerificationRepository.save(otpVerification);

        log.info("OTP verified: channel={} target={}", otpVerification.getChannel(), maskTarget(otpVerification.getTarget()));

        return VerifyOtpResponse.builder()
                .verified(true)
                .message(otpVerification.getChannel() == OtpChannel.EMAIL
                        ? "Email verified successfully"
                        : "Mobile number verified successfully")
                .build();
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredOtps() {
        try {
            otpVerificationRepository.deleteExpiredBefore(Instant.now().minus(EXPIRED_RETENTION));
        } catch (Exception ex) {
            log.error("Failed to clean up expired OTPs: {}", ex.getMessage(), ex);
        }
    }

    // ── internals ─────────────────────────────────────────────────────────

    private void enforceRateLimit(String target, OtpChannel channel) {
        otpVerificationRepository.findFirstByTargetAndChannelOrderByCreatedAtDesc(target, channel)
                .ifPresent(last -> {
                    long waited = Duration.between(last.getCreatedAt(), Instant.now()).getSeconds();
                    if (waited < RESEND_COOLDOWN_SECONDS) {
                        throw new OtpRateLimitExceededException("Please wait "
                                + (RESEND_COOLDOWN_SECONDS - waited) + " seconds before requesting another code.");
                    }
                });
        Instant windowStart = Instant.now().minus(RATE_LIMIT_WINDOW_MINUTES, ChronoUnit.MINUTES);
        long recentRequests = otpVerificationRepository.countRecentRequests(target, channel, windowStart);
        if (recentRequests >= RATE_LIMIT_MAX_REQUESTS) {
            throw new OtpRateLimitExceededException(
                    "Too many verification code requests. Please try again in a few minutes.");
        }
    }

    private String normalizeTarget(OtpChannel channel, String rawTarget) {
        String target = rawTarget.trim();
        if (channel == OtpChannel.EMAIL) {
            target = target.toLowerCase(Locale.ROOT);
            if (!EMAIL_PATTERN.matcher(target).matches()) {
                throw new IllegalArgumentException("Invalid email address");
            }
        } else {
            if (!E164_PATTERN.matcher(target).matches()) {
                throw new IllegalArgumentException(
                        "Invalid mobile number. Use E.164 format, e.g. +14155552671");
            }
        }
        return target;
    }

    private String generateOtp() {
        int otp = secureRandom.nextInt(900_000) + 100_000;
        return String.valueOf(otp);
    }

    private String hash(String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(otp.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    private String maskTarget(String target) {
        if (target.contains("@")) {
            int at = target.indexOf('@');
            String local = target.substring(0, at);
            String masked = local.length() <= 2 ? local : local.charAt(0) + "***" + local.charAt(local.length() - 1);
            return masked + target.substring(at);
        }
        return target.length() <= 4 ? target : "***" + target.substring(target.length() - 4);
    }
}
