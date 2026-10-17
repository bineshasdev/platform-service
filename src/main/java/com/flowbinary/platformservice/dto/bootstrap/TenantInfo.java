package com.flowbinary.platformservice.dto.bootstrap;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TenantInfo {
    private String id;
    private String name;
    private String slug;
    private String domain;
    private String plan;
    private String status;
}
