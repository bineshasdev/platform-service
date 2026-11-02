package com.flowbinary.platformservice.dto.user;

import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
public class UserUpdateRequest {

    private String firstName;

    private String lastName;

    private String mobile;

    @Pattern(regexp = "ACTIVE|INACTIVE|SUSPENDED",
             message = "status must be ACTIVE, INACTIVE, or SUSPENDED")
    private String status;
}
