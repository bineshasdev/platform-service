package com.flowbinary.platformservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class OtpDeliveryException extends RuntimeException {

    public OtpDeliveryException(String message) {
        super(message);
    }

    public OtpDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
