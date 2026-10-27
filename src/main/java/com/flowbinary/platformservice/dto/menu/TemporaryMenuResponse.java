package com.flowbinary.platformservice.dto.menu;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TemporaryMenuResponse(String code, String label, String description, String icon, String route,
                                    String type, String target, String parentCode, int sortOrder, int priority,
                                    String badge, String status, Instant startsAt, Instant endsAt,
                                    /** Whether users can see it right now. */
                                    boolean live,
                                    String requiredPrivilege, String requiredFeature,
                                    String audienceMode, List<String> roles, List<UUID> users, List<String> groups,
                                    UUID createdBy) {
}
