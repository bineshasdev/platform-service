package com.flowbinary.platformservice.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * A menu catalogue changed. Tells every instance to re-read its version
 * counters now instead of at the next periodic check. {@code tenantId} null
 * means the platform-wide menu changed.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class MenuCatalogChangedEvent extends PlatformEvent {

    private String applicationCode;
    private UUID tenantId;

    @Override
    public String getEventType() {
        return "MENU_CATALOG_CHANGED";
    }
}
