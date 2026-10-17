package com.flowbinary.platformservice.dto.registration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** New email address or E.164 mobile number, depending on the channel in the path. */
@Getter
@Setter
public class ContactChangeRequest {

    @NotBlank(message = "value is required")
    @Size(max = 255)
    private String value;
}
