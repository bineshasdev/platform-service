package com.flowbinary.platformservice.dto.user;

import com.flowbinary.platformservice.model.PlatformRole;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
public class UserUpdateRequest {

    private String firstName;

    private String lastName;

    /** Any platform role except SUPERADMIN (rejected in {@code UserService.updateUser}). */
    private PlatformRole role;

    private String mobile;

    @Pattern(regexp = "ACTIVE|INACTIVE|SUSPENDED",
             message = "status must be ACTIVE, INACTIVE, or SUSPENDED")
    private String status;
}
