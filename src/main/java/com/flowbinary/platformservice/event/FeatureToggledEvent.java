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
public class FeatureToggledEvent extends PlatformEvent {

    private UUID tenantId;
    private String featureCode;
    private boolean enabled;
    private String toggledBy;

    @Override
    public String getEventType() {
        return "FEATURE_TOGGLED";
    }
}
