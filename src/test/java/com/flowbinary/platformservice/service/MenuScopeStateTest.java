package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.model.MenuOp;
import com.flowbinary.platformservice.model.MenuOpType;
import com.flowbinary.platformservice.service.MenuScopeState.Kind;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class MenuScopeStateTest {

    private static Map<String, Object> item(String parent, String label, int order) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("parentCode", parent);
        a.put("label", label);
        a.put("type", "LINK");
        a.put("behavior", "NAVIGATE");
        a.put("target", "SELF");
        a.put("separatorBefore", false);
        a.put("visible", true);
        a.put("enabled", true);
        a.put("sortOrder", order);
        a.put("plans", List.of("free"));
        a.put("roles", List.of("DOCTOR"));
        return a;
    }

    @Test
    void diffClassifiesCreateUpdateDelete() {
        Map<String, Object> before = new TreeMap<>(Map.of("item:a", item(null, "A", 1), "item:b", item(null, "B", 2)));
        Map<String, Object> after = new TreeMap<>(Map.of("item:a", item(null, "A2", 1), "item:c", item(null, "C", 3)));
        var changes = MenuScopeState.diff(before, after);
        assertThat(changes).extracting(c -> c.key() + ":" + c.kind())
                .containsExactly("item:a:UPDATE", "item:b:DELETE", "item:c:CREATE");
    }

    @Test
    void diffIgnoresJsonRoundTripNumberAndListTypes() {
        Map<String, Object> a = item(null, "A", 1);
        Map<String, Object> b = item(null, "A", 1);
        b.put("sortOrder", 1L);                       // Integer vs Long after a jsonb round trip
        b.put("plans", new ArrayList<>(List.of("free")));
        assertThat(MenuScopeState.diff(Map.of("item:a", a), Map.of("item:a", b))).isEmpty();
    }

    @Test
    void rollbackOpsCreateParentsBeforeChildrenAndDeleteChildrenFirst() {
        Map<String, Object> target = new TreeMap<>();
        target.put("item:child", item("parent", "Child", 1));
        target.put("item:parent", item(null, "Parent", 1));
        Map<String, Object> current = new TreeMap<>();
        current.put("item:gone-child", item("gone", "x", 1));
        current.put("item:gone", item(null, "x", 1));

        List<MenuOp> ops = MenuScopeState.opsToReach(current, target);
        List<String> seq = ops.stream().map(o -> o.op() + ":" + o.code()).toList();

        assertThat(seq).containsExactly("PUT_ITEM:parent", "PUT_ITEM:child",
                "DELETE_ITEM:gone-child", "DELETE_ITEM:gone");
    }

    @Test
    void rollbackCarriesAssignmentsAndOverrides() {
        UUID user = UUID.randomUUID();
        Map<String, Object> target = new TreeMap<>();
        target.put("grants:dash", Map.of("roles", List.of("CUSTOM")));
        target.put("override:dash", new LinkedHashMap<>(Map.of("hidden", true)));
        Map<String, Object> current = new TreeMap<>();
        current.put("user:dash:" + user, Map.of("effect", "DENY"));

        List<MenuOp> ops = MenuScopeState.opsToReach(current, target);

        assertThat(ops).extracting(MenuOp::op).containsExactlyInAnyOrder(
                MenuOpType.SET_ROLES, MenuOpType.SET_OVERRIDE, MenuOpType.SET_USER_ACCESS);
        MenuOp clear = ops.stream().filter(o -> o.op() == MenuOpType.SET_USER_ACCESS).findFirst().orElseThrow();
        assertThat(clear.effect()).isNull();            // null effect clears the assignment
        assertThat(clear.userId()).isEqualTo(user);
        assertThat(Kind.values()).hasSize(3);
    }

    @Test
    void placementAndTenantTypesAreCapturedAndRestored() {
        var app = com.flowbinary.platformservice.model.Application.builder().code("A").name("A").build();
        app.setId(UUID.randomUUID());
        var item = MenuTestFixtures.item("lab.worklist", null);
        item.setPlacement(com.flowbinary.platformservice.model.MenuPlacement.TAB);
        item.setTenantTypes(List.of("LAB"));

        var state = MenuScopeState.capture(new MenuScope(app, null), List.of(item), List.of(), List.of(), List.of());
        @SuppressWarnings("unchecked") Map<String, Object> atom = (Map<String, Object>) state.get("item:lab.worklist");
        assertThat(atom).containsEntry("placement", "TAB").containsEntry("tenantTypes", List.of("LAB"));

        MenuOp put = MenuScopeState.opsToReach(Map.of(), state).get(0);
        assertThat(put.placement()).isEqualTo("TAB");
        assertThat(put.tenantTypes()).containsExactly("LAB");
    }

    @Test
    void aSnapshotFromBeforePlacementRestoresTheDefaults() {
        // snapshots published before V25 have neither key
        MenuOp put = MenuScopeState.opsToReach(Map.of(), Map.of("item:a", item(null, "A", 1))).get(0);
        assertThat(put.placement()).isNull();          // PUT_ITEM then defaults to LEFT
        assertThat(put.tenantTypes()).isEmpty();       // every tenant type
    }

    @Test
    void aModuleItemContributesOnlyItsAdminControlledPartAndOnlyToTheGlobalScope() {
        var app = com.flowbinary.platformservice.model.Application.builder().code("A").name("A").build();
        app.setId(UUID.randomUUID());
        var item = MenuTestFixtures.item("billing.inv", null);
        item.setSource(com.flowbinary.platformservice.model.MenuSource.MODULE);
        item.setIsActive(false);
        item.setSortOrder(42);
        var tenant = com.flowbinary.platformservice.model.Tenant.builder().build();
        tenant.setId(UUID.randomUUID());

        var global = MenuScopeState.capture(new MenuScope(app, null), List.of(item), List.of(), List.of(), List.of());
        assertThat(global).containsOnlyKeys("moditem:billing.inv");
        @SuppressWarnings("unchecked") Map<String, Object> atom = (Map<String, Object>) global.get("moditem:billing.inv");
        assertThat(atom).containsEntry("enabled", false).containsEntry("sortOrder", 42);

        assertThat(MenuScopeState.capture(new MenuScope(app, tenant), List.of(item), List.of(), List.of(), List.of())).isEmpty();
    }

    @Test
    void rollbackRestoresHideAndOrderOfModuleItemsButIgnoresItemsTheModuleAddedOrDropped() {
        Map<String, Object> was = new TreeMap<>();
        was.put("moditem:billing.a", new LinkedHashMap<>(Map.of("enabled", true, "sortOrder", 5)));
        was.put("moditem:billing.dropped", new LinkedHashMap<>(Map.of("enabled", true, "sortOrder", 1)));
        Map<String, Object> now = new TreeMap<>();
        now.put("moditem:billing.a", new LinkedHashMap<>(Map.of("enabled", false, "sortOrder", 9)));
        now.put("moditem:billing.added", new LinkedHashMap<>(Map.of("enabled", true, "sortOrder", 2)));

        List<MenuOp> ops = MenuScopeState.opsToReach(now, was);

        assertThat(ops).hasSize(1);
        MenuOp op = ops.get(0);
        assertThat(op.op()).isEqualTo(MenuOpType.PATCH_ITEM);
        assertThat(op.code()).isEqualTo("billing.a");
        assertThat(op.enabled()).isTrue();
        assertThat(op.sortOrder()).isEqualTo(5);
    }
}
