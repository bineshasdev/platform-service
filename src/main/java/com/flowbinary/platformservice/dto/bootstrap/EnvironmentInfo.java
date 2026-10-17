package com.flowbinary.platformservice.dto.bootstrap;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EnvironmentInfo {
    private String region;
    private String deployment;
    private String buildNumber;
}
