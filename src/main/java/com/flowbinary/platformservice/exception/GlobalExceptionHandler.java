package com.flowbinary.platformservice.exception;

import com.flowbinary.platformservice.security.TenantNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Validation ────────────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                        (a, b) -> a));

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Validation failed");
        pd.setType(URI.create("urn:problem:validation-error"));
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Bad request");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("urn:problem:bad-request"));
        return pd;
    }

    // ── Not found ─────────────────────────────────────────────────────────────

    @ExceptionHandler(java.util.NoSuchElementException.class)
    ProblemDetail handleNotFound(java.util.NoSuchElementException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setTitle("Not found");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("urn:problem:not-found"));
        return pd;
    }

    // ── Tenant / Security ─────────────────────────────────────────────────────

    @ExceptionHandler(TenantNotFoundException.class)
    ProblemDetail handleTenantNotFound(TenantNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        pd.setTitle("Tenant not found");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("urn:problem:tenant-not-found"));
        return pd;
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        pd.setTitle("Access denied");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("urn:problem:access-denied"));
        return pd;
    }

    // ── Menu administration ───────────────────────────────────────────────────

    @ExceptionHandler(MenuDraftException.class)
    ProblemDetail handleMenuDraft(MenuDraftException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        pd.setTitle("Menu change rejected");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("urn:problem:menu-draft-invalid"));
        if (ex.getOpIndex() >= 0) pd.setProperty("opIndex", ex.getOpIndex());
        return pd;
    }

    @ExceptionHandler(MenuHierarchyException.class)
    ProblemDetail handleMenuHierarchy(MenuHierarchyException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        pd.setTitle("Menu hierarchy invalid");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("urn:problem:menu-hierarchy"));
        pd.setProperty("reason", ex.getReason().name());
        return pd;
    }

    @ExceptionHandler(AuthzRuleException.class)
    ProblemDetail handleAuthzRule(AuthzRuleException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        pd.setTitle("Access change rejected");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("urn:problem:authz-rule"));
        pd.setProperty("rule", ex.getRule().name());
        return pd;
    }

    @ExceptionHandler({MenuConflictException.class, org.springframework.dao.OptimisticLockingFailureException.class})
    ProblemDetail handleMenuConflict(RuntimeException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        pd.setTitle("Conflict");
        pd.setDetail(ex instanceof MenuConflictException ? ex.getMessage()
                : "The record was changed by someone else; reload and retry");
        pd.setType(URI.create("urn:problem:conflict"));
        return pd;
    }

    // ── Storage ───────────────────────────────────────────────────────────────

    @ExceptionHandler(QuotaExceededException.class)
    ProblemDetail handleQuotaExceeded(QuotaExceededException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.PAYLOAD_TOO_LARGE);
        pd.setTitle("Storage quota exceeded");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("urn:problem:quota-exceeded"));
        return pd;
    }

    @ExceptionHandler(FileTypeNotAllowedException.class)
    ProblemDetail handleFileTypeNotAllowed(FileTypeNotAllowedException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        pd.setTitle("File type not allowed");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("urn:problem:file-type-not-allowed"));
        return pd;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.PAYLOAD_TOO_LARGE);
        pd.setTitle("File too large");
        pd.setDetail("The uploaded file exceeds the maximum allowed upload size");
        pd.setType(URI.create("urn:problem:file-too-large"));
        return pd;
    }

    @ExceptionHandler(StorageException.class)
    ProblemDetail handleStorage(StorageException ex) {
        log.error("Storage error: {}", ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setTitle("Storage error");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("urn:problem:storage-error"));
        return pd;
    }

    // ── OTP ───────────────────────────────────────────────────────────────────

    @ExceptionHandler(InvalidOtpException.class)
    ProblemDetail handleInvalidOtp(InvalidOtpException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Invalid OTP");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("urn:problem:invalid-otp"));
        return pd;
    }

    @ExceptionHandler(OtpExpiredException.class)
    ProblemDetail handleOtpExpired(OtpExpiredException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("OTP expired");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("urn:problem:otp-expired"));
        return pd;
    }

    @ExceptionHandler(OtpAttemptsExceededException.class)
    ProblemDetail handleOtpAttemptsExceeded(OtpAttemptsExceededException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
        pd.setTitle("Too many attempts");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("urn:problem:otp-attempts-exceeded"));
        return pd;
    }

    @ExceptionHandler(OtpRateLimitExceededException.class)
    ProblemDetail handleOtpRateLimitExceeded(OtpRateLimitExceededException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
        pd.setTitle("Rate limit exceeded");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("urn:problem:otp-rate-limit-exceeded"));
        return pd;
    }

    @ExceptionHandler(OtpDeliveryException.class)
    ProblemDetail handleOtpDelivery(OtpDeliveryException ex) {
        log.error("OTP delivery failed: {}", ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY);
        pd.setTitle("Failed to deliver OTP");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("urn:problem:otp-delivery-failed"));
        return pd;
    }

    // ── Catch-all ─────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setTitle("Internal server error");
        pd.setDetail("An unexpected error occurred");
        pd.setType(URI.create("urn:problem:internal-error"));
        return pd;
    }
}
