package com.flowbinary.platformservice.dto.user;

import java.util.UUID;
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

    /** Primary role of the new member: a role of this tenant. Omitted = the type's least-privileged template. */
    private UUID primaryRoleId;

    @NotBlank
    private String temporaryPassword;

    private String mobile;
}
