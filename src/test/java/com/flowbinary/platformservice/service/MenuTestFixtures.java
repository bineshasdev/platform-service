package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.model.*;

import java.util.Set;
import java.util.UUID;

/** Builders for in-memory menu trees; no database involved. */
final class MenuTestFixtures {

    static final Application APP = app();

    private MenuTestFixtures() {
    }

    private static Application app() {
        Application a = Application.builder().code("PROVIDER_CONSOLE").name("Console").build();
        a.setId(UUID.randomUUID());
        return a;
    }

    static MenuItem item(String code, MenuItem parent) {
        MenuItem m = MenuItem.builder()
                .application(APP).parent(parent).code(code).label(code)
                .route("/" + code).type(MenuItemType.LINK).sortOrder(0)
                .depth(parent == null ? 0 : parent.getDepth() + 1)
                .path(parent == null ? code : parent.getPath() + "/" + code)
                .build();
        m.setId(UUID.randomUUID());
        return m;
    }

    static MenuItem withPlans(MenuItem m, TenantPlan... plans) {
        for (TenantPlan p : plans) {
            m.getPlanAccess().add(MenuPlanAccess.builder()
                    .id(new MenuPlanAccessId(m.getId(), p)).menuItem(m).build());
        }
        return m;
    }

    /** An item every plan can see and no privilege gates. */
    static MenuItem open(String code, MenuItem parent) {
        return withPlans(item(code, parent), TenantPlan.values());
    }

    static Set<UUID> ids(MenuItem... items) {
        return java.util.Arrays.stream(items).map(MenuItem::getId).collect(java.util.stream.Collectors.toSet());
    }
}
