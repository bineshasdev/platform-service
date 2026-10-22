package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.dto.bootstrap.MenuEntry;
import com.flowbinary.platformservice.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.flowbinary.platformservice.service.MenuTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class MenuTreeBuilderTest {

    private final Role role = role("DOCTOR");

    private List<MenuEntry> build(List<MenuItem> all, MenuItem... visible) {
        return MenuTreeBuilder.build(all, ids(visible), Map.of(), "en");
    }

    @Test
    void parentWithVisibleChildIsKeptWithChild() {
        MenuItem admin = open("admin", null, role);
        MenuItem users = open("users", admin, role);
        List<MenuEntry> tree = build(List.of(admin, users), admin, users);
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getChildren()).extracting(MenuEntry::getId).containsExactly("users");
    }

    @Test
    void parentWithRouteButNoVisibleChildrenIsPruned() {
        MenuItem admin = open("admin", null, role);          // has its own route
        MenuItem users = open("users", admin, role);
        assertThat(build(List.of(admin, users), admin)).isEmpty();   // child not visible to caller
    }

    @Test
    void leafWithoutChildrenInCatalogueIsKept() {
        MenuItem home = open("home", null, role);
        assertThat(build(List.of(home), home)).extracting(MenuEntry::getId).containsExactly("home");
    }

    @Test
    void childOfHiddenParentIsDropped() {
        MenuItem admin = open("admin", null, role);
        MenuItem users = open("users", admin, role);
        assertThat(build(List.of(admin, users), users)).isEmpty();
    }

    @Test
    void childlessRoutelessGroupIsPruned() {
        MenuItem g = open("grp", null, role);
        g.setType(MenuItemType.GROUP);
        g.setRoute(null);
        assertThat(build(List.of(g), g)).isEmpty();
    }

    @Test
    void pruningCascadesUpThroughLevels() {
        MenuItem a = open("a", null, role);
        MenuItem b = open("b", a, role);
        MenuItem c = open("c", b, role);
        assertThat(build(List.of(a, b, c), a, b)).isEmpty();
        assertThat(build(List.of(a, b, c), a, b, c)).hasSize(1);
    }

    @Test
    void siblingsSortedByOrderThenCode() {
        MenuItem x = open("x", null, role); x.setSortOrder(2);
        MenuItem b = open("b", null, role); b.setSortOrder(1);
        MenuItem a = open("a", null, role); a.setSortOrder(1);
        assertThat(build(List.of(x, b, a), x, b, a)).extracting(MenuEntry::getId).containsExactly("a", "b", "x");
    }

    @Test
    void tenantOverrideRelabelsAndReorders() {
        MenuItem a = open("a", null, role); a.setSortOrder(1);
        MenuItem b = open("b", null, role); b.setSortOrder(2);
        MenuTenantOverride o = MenuTenantOverride.builder().menuItem(b).label("Bee").sortOrder(0).isHidden(false).build();
        List<MenuEntry> tree = MenuTreeBuilder.build(List.of(a, b), ids(a, b), Map.of(b.getId(), o), "en");
        assertThat(tree).extracting(MenuEntry::getLabel).containsExactly("Bee", "a");
        assertThat(tree.get(0).getOrder()).isZero();
    }

    @Test
    void dividersAreCollapsed() {
        MenuItem d1 = open("d1", null, role); d1.setType(MenuItemType.DIVIDER); d1.setRoute(null); d1.setSortOrder(0);
        MenuItem a  = open("a", null, role);  a.setSortOrder(1);
        MenuItem d2 = open("d2", null, role); d2.setType(MenuItemType.DIVIDER); d2.setRoute(null); d2.setSortOrder(2);
        MenuItem d3 = open("d3", null, role); d3.setType(MenuItemType.DIVIDER); d3.setRoute(null); d3.setSortOrder(3);
        MenuItem b  = open("b", null, role);  b.setSortOrder(4);
        MenuItem d4 = open("d4", null, role); d4.setType(MenuItemType.DIVIDER); d4.setRoute(null); d4.setSortOrder(5);
        assertThat(build(List.of(d1, a, d2, d3, b, d4), d1, a, d2, d3, b, d4))
                .extracting(MenuEntry::getId).containsExactly("a", "d2", "b");
    }

    @Test
    void parentHoldingOnlyDividersIsPruned() {
        MenuItem p = open("p", null, role);
        MenuItem d = open("d", p, role); d.setType(MenuItemType.DIVIDER); d.setRoute(null);
        assertThat(build(List.of(p, d), p, d)).isEmpty();
    }

    @Test
    void entryCarriesNewFields() {
        MenuItem a = open("a", null, role);
        a.setBehavior(MenuBehavior.BOTH);
        a.setSeparatorBefore(true);
        MenuEntry e = build(List.of(a), a).get(0);
        assertThat(e.getType()).isEqualTo("LINK");
        assertThat(e.getBehavior()).isEqualTo("BOTH");
        assertThat(e.getTarget()).isEqualTo("SELF");
        assertThat(e.getSeparatorBefore()).isTrue();
        assertThat(e.getRoles()).containsExactly("DOCTOR");
    }
}
