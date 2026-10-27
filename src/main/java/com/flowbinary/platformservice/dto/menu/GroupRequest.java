package com.flowbinary.platformservice.dto.menu;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code code} is used on create only; {@code active} null leaves it unchanged. */
public record GroupRequest(String code,
                           @NotBlank @Size(max = 100) String name,
                           @Size(max = 500) String description,
                           Boolean active) {
}
