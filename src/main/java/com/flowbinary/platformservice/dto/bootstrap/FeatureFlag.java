package com.flowbinary.platformservice.dto.bootstrap;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FeatureFlag {
    private boolean enabled;
    /** null = unlimited */
    private Long    limit;
    private String  resetPeriod;
}
