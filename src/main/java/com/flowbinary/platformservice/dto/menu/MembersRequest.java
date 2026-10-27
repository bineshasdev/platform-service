package com.flowbinary.platformservice.dto.menu;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record MembersRequest(@NotEmpty @Size(max = 500) Set<UUID> userIds) {
}
