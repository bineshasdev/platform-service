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

    static MenuItem withRole(MenuItem m, Role role) {
        m.getRoleAccess().add(MenuRoleAccess.builder()
                .id(new MenuRoleAccessId(m.getId(), role.getId())).menuItem(m).role(role).build());
        return m;
    }

    static Role role(String code) {
        Role r = Role.builder().code(code).name(code).build();
        r.setId(UUID.randomUUID());
        return r;
    }

    static Privilege privilege(String code) {
        Privilege p = Privilege.builder().code(code).name(code).category("X").build();
        p.setId(UUID.randomUUID());
        return p;
    }

    /** An item every plan and the given role can see. */
    static MenuItem open(String code, MenuItem parent, Role role) {
        return withRole(withPlans(item(code, parent), TenantPlan.values()), role);
    }

    static Set<UUID> ids(MenuItem... items) {
        return java.util.Arrays.stream(items).map(MenuItem::getId).collect(java.util.stream.Collectors.toSet());
    }
}
