package com.flowbinary.platformservice.dto.otp;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VerifyOtpResponse {
    private boolean verified;
    private String message;
}
