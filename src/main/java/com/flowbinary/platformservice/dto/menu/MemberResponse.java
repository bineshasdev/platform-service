package com.flowbinary.platformservice.dto.menu;

import java.util.UUID;

public record MemberResponse(UUID userId, String email, String username, String name) {
}
