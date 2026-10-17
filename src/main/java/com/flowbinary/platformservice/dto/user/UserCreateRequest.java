package com.flowbinary.platformservice.dto.user;

import com.flowbinary.platformservice.model.PlatformRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class UserCreateRequest {

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotBlank
    @Email
    private String email;

    @NotNull
    private PlatformRole role;

    @NotBlank
    private String temporaryPassword;

    private String mobile;
}
