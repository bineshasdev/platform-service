package com.flowbinary.platformservice.dto.registration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ContactOtpRequest {

    @NotBlank(message = "otp is required")
    @Pattern(regexp = "\\d{6}", message = "otp must be 6 digits")
    private String otp;
}
