package com.flowbinary.platformservice.dto.plan;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlanResponse {
    private UUID id;
    private String name;
    private String code;
    private BigDecimal price;
    private String billingCycle;
    private List<PlanFeatureDetail> features;
}
