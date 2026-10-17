package com.flowbinary.platformservice.dto.registration;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClinicEmailChangeRequest {

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid address")
    private String email;
}
