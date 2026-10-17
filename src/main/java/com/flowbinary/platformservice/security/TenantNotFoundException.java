package com.flowbinary.platformservice.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class TenantNotFoundException extends RuntimeException {

    public TenantNotFoundException(String message) {
        super(message);
    }
}
