package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.exception.MenuDraftException;
import com.flowbinary.platformservice.model.*;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MenuOpValidatorTest {

    private static final Application APP = Application.builder().code("A").name("A").build();
    private static final MenuScope GLOBAL = new MenuScope(APP, null);
    private static final MenuScope TENANT = new MenuScope(APP, Tenant.builder().build());

    private static MenuOp.MenuOpBuilder put() {
        return MenuOp.builder().op(MenuOpType.PUT_ITEM).code("ok").label("OK");
    }

    private static void reject(MenuOp op, MenuScope scope, String fragment) {
        assertThatThrownBy(() -> MenuOpValidator.validate(0, op, scope))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining(fragment);
    }

    @Test
    void acceptsAWellFormedItem() {
        assertThatCode(() -> MenuOpValidator.validate(0, put().route("/a/b?x=1").icon("Calendar").build(), TENANT))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsafeRoutes() {
        for (String bad : new String[]{"javascript:alert(1)", "//evil.example", "http://x", "data:text/html,x", "a/b", "/a b"}) {
            reject(put().route(bad).build(), TENANT, "route");
        }
    }

    @Test
    void externalMustBeHttps() {
        assertThatCode(() -> MenuOpValidator.validate(0, put().type("EXTERNAL").route("https://example.org/x").build(), TENANT))
                .doesNotThrowAnyException();
        reject(put().type("EXTERNAL").route("http://example.org").build(), TENANT, "https");
        reject(put().type("EXTERNAL").route("javascript:1").build(), TENANT, "https");
    }

    @Test
    void rejectsBadCodesIconsAndEnums() {
        reject(put().code("Has Space").build(), TENANT, "code");
        reject(put().code("UPPER").build(), TENANT, "code");
        reject(put().icon("<script>").build(), TENANT, "icon");
        reject(put().type("BOGUS").build(), TENANT, "type");
        reject(put().behavior("BOGUS").build(), TENANT, "behavior");
        reject(put().plans(Set.of("platinum")).build(), TENANT, "plans");
        reject(put().label(" ").build(), TENANT, "label");
    }

    @Test
    void placementAndTenantTypesMustBeKnown() {
        assertThatCode(() -> MenuOpValidator.validate(0,
                put().placement("SETTINGS").tenantTypes(Set.of("CLINIC", "LAB")).build(), GLOBAL))
                .doesNotThrowAnyException();
        reject(put().placement("SIDEBAR").build(), GLOBAL, "placement");
        reject(put().tenantTypes(Set.of("HOSPITAL")).build(), GLOBAL, "tenantTypes");
    }

    @Test
    void tenantTypesAreForGlobalItemsOnly() {
        reject(put().tenantTypes(Set.of("LAB")).build(), TENANT, "global items");
        reject(MenuOp.builder().op(MenuOpType.PATCH_ITEM).code("x").tenantTypes(Set.of("LAB")).build(), TENANT, "global items");
        // empty = every type, which is what a tenant item already is
        assertThatCode(() -> MenuOpValidator.validate(0, put().tenantTypes(Set.of()).build(), TENANT))
                .doesNotThrowAnyException();
    }

    @Test
    void tenantOnlyOperationsAreRefusedInGlobalScope() {
        reject(MenuOp.builder().op(MenuOpType.SET_OVERRIDE).code("x").build(), GLOBAL, "tenant scope");
        reject(MenuOp.builder().op(MenuOpType.SET_USER_ACCESS).code("x").userId(UUID.randomUUID()).build(), GLOBAL, "tenant scope");
    }

    @Test
    void reorderNeedsDistinctCodes() {
        reject(MenuOp.builder().op(MenuOpType.REORDER).orderedCodes(java.util.List.of("a", "a")).build(), TENANT, "duplicates");
        reject(MenuOp.builder().op(MenuOpType.REORDER).build(), TENANT, "orderedCodes");
    }

    @Test
    void userAccessEffectMustBeKnown() {
        reject(MenuOp.builder().op(MenuOpType.SET_USER_ACCESS).code("x").userId(UUID.randomUUID()).effect("ALLOW").build(),
                TENANT, "effect");
    }
}
