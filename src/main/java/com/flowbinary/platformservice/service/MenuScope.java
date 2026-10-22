package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.model.Application;
import com.flowbinary.platformservice.model.Tenant;

import java.util.UUID;

/**
 * What a draft, a publish and a snapshot belong to: one application, and
 * either the global menu ({@code tenant == null}) or one tenant's own layer.
 * A tenant scope can never change global rows.
 */
public record MenuScope(Application application, Tenant tenant) {

    public boolean isGlobal() {
        return tenant == null;
    }

    public UUID tenantId() {
        return tenant == null ? null : tenant.getId();
    }

    public String label() {
        return application.getCode() + (tenant == null ? "/global" : "/tenant:" + tenant.getId());
    }
}
