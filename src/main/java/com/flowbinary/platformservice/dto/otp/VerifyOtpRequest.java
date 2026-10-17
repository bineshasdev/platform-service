package com.flowbinary.platformservice.dto.otp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
public class VerifyOtpRequest {

    @NotBlank(message = "verificationToken is required")
    private String verificationToken;

    @NotBlank(message = "otp is required")
    @Pattern(regexp = "\\d{6}", message = "otp must be 6 digits")
    private String otp;
}
