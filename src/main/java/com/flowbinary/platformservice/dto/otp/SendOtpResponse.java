package com.flowbinary.platformservice.dto.otp;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SendOtpResponse {
    private String verificationToken;
    private long expiresInSeconds;
    private String message;
}
