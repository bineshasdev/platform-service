package com.flowbinary.platformservice.dto.plan;

import com.flowbinary.platformservice.model.TenantPlan;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpgradeRequest {
    @NotNull
    private TenantPlan targetPlan;
}
