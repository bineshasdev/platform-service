package com.flowbinary.platformservice.dto.registration;

import com.flowbinary.platformservice.domain.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Payload for POST /api/v1/registration/patients — public patient self-signup.
 *
 * <p>Creates a platform-global {@code Patient} account (no tenant). Contact
 * details are unverified at creation; verify later via the OTP endpoints.
 */
@Getter
@Setter
public class PatientRegistrationRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 50)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 50)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    /** E.164 mobile number, e.g. +919812345678. */
    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "Mobile must be in E.164 format, e.g. +919812345678")
    private String phone;

    private Gender gender;

    private LocalDate dateOfBirth;

    @Size(max = 40)
    private String preferredLanguage;

    private boolean marketingOptIn;
}
