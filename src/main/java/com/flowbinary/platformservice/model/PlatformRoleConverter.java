package com.flowbinary.platformservice.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

/**
 * JPA converter that persists {@link PlatformRole} as its lowercase {@code roleName()}
 * (e.g. {@code "tenantadmin"}) instead of the enum constant name ({@code "TENANT_ADMIN"}).
 */
@Converter
public class PlatformRoleConverter implements AttributeConverter<PlatformRole, String> {

    @Override
    public String convertToDatabaseColumn(PlatformRole role) {
        return role == null ? null : role.roleName();
    }

    @Override
    public PlatformRole convertToEntityAttribute(String dbValue) {
        if (dbValue == null) return null;
        return Arrays.stream(PlatformRole.values())
                .filter(r -> r.roleName().equals(dbValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + dbValue));
    }
}
