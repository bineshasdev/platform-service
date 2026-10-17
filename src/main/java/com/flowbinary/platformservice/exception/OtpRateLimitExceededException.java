package com.flowbinary.platformservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class OtpRateLimitExceededException extends RuntimeException {

    public OtpRateLimitExceededException(String message) {
        super(message);
    }
}
