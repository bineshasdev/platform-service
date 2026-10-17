package com.flowbinary.platformservice.dto.plan;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlanFeatureDetail {
    private String featureCode;
    private String featureName;
    private String category;
    private Boolean enabled;
    private Long usageLimit;
}
