package com.flowbinary.platformservice.dto.bootstrap;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class IntegrationsInfo {
    private List<String> enabled;
}
