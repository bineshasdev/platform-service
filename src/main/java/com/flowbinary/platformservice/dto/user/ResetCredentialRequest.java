package com.flowbinary.platformservice.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class ResetCredentialRequest {

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String newPassword;

    /**
     * When {@code true} the user is forced to change the password on next login.
     * Defaults to {@code true}.
     */
    private boolean temporary = true;
}
