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
}
