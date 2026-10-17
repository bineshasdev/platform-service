package com.flowbinary.platformservice.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {
    private UUID id;
    private UUID authUserId;
    private UUID tenantId;
    private String firstName;
    private String lastName;
    private String email;
    private String role;
    private String mobile;
    private String status;
    private UUID   profileImageId;
    private String profileImageUrl;
    private Instant createdAt;
}
