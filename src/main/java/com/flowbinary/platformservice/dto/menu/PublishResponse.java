package com.flowbinary.platformservice.dto.menu;

import java.util.UUID;

public record PublishResponse(int version, int changes, UUID snapshotId) {
}
