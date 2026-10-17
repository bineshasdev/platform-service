package com.flowbinary.platformservice.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class MenuItemUpdatedEvent extends PlatformEvent {

    private UUID menuItemId;
    private String code;
    private String action; // CREATED | UPDATED | DELETED

    @Override
    public String getEventType() {
        return "MENU_ITEM_UPDATED";
    }
}
