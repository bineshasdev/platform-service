package com.flowbinary.platformservice.dto.otp;

import com.flowbinary.platformservice.model.OtpChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class SendOtpRequest {

    @NotNull(message = "channel is required (EMAIL or SMS)")
    private OtpChannel channel;

    /** Email address (EMAIL channel) or E.164 mobile number (SMS channel). */
    @NotBlank(message = "target is required")
    private String target;

    /** Free-form purpose label, e.g. VERIFY_CONTACT, SIGNUP, PASSWORD_RESET. */
    private String purpose;
}
