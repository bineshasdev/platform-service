package com.flowbinary.platformservice.dto.bootstrap;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlatformInfo {
    private String name;
    private String version;
    private String environment;
    private String supportUrl;
    private String documentationUrl;
}
