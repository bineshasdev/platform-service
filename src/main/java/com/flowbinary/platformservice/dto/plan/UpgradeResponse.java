package com.flowbinary.platformservice.dto.plan;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class UpgradeResponse {
    private UUID subscriptionId;
    private String previousPlan;
    private String newPlan;
    private String status;
    private Instant effectiveFrom;
}
