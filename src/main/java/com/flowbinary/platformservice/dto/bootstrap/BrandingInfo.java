package com.flowbinary.platformservice.dto.bootstrap;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BrandingInfo {
    private String logo;
    private String logoUrl;
    private String favicon;
    private String faviconUrl;
    private String primaryColor;
    private String secondaryColor;
    private String font;
}
