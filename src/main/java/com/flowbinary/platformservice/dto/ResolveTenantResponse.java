package com.flowbinary.platformservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResolveTenantResponse {
    private String tenantId;
    private String displayName;
    private String slug;
    private String realmName;
    private String keycloakServerUrl;
    private String uiClientId;
    private String logoUrl;
    private String plan;
}
