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
public class TenantPlanChangedEvent extends PlatformEvent {

    private UUID tenantId;
    private String previousPlan;
    private String newPlan;
    private String changedBy;

    @Override
    public String getEventType() {
        return "TENANT_PLAN_CHANGED";
    }
}
