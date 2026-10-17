package com.flowbinary.platformservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
public class QuotaExceededException extends RuntimeException {

    public QuotaExceededException(long usedBytes, long quotaBytes) {
        super(String.format(
            "Storage quota exceeded: using %d bytes of %d byte limit",
            usedBytes, quotaBytes));
    }
}
