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
        return MenuTreeBuilder.build(all.stream().map(CatalogItem::of).toList(), ids(visible), Map.of(), "en");
    }

    // ── Placement ───────────────────────────────────────────────────────────

    @Test
    void entriesCarryTheirPlacement() {
        MenuItem settings = open("settings", null, role);
        MenuItem staff = open("settings.staff", settings, role);
        staff.setPlacement(MenuPlacement.SETTINGS);
        List<MenuEntry> tree = build(List.of(settings, staff), settings, staff);
        assertThat(tree.get(0).getPlacement()).isEqualTo("LEFT");
        assertThat(tree.get(0).getChildren().get(0).getPlacement()).isEqualTo("SETTINGS");
    }

    @Test
    void forPlacementReturnsTheTopmostEntriesOfThatPlacementWithTheirChildren() {
        MenuItem home = open("home", null, role);
        MenuItem settings = open("settings", null, role);
        MenuItem general = open("settings.general", settings, role);
        general.setPlacement(MenuPlacement.SETTINGS);
        general.setType(MenuItemType.GROUP);
        MenuItem org = open("settings.organisation", general, role);
        org.setPlacement(MenuPlacement.SETTINGS);
        MenuItem quick = open("header.quick", null, role);
        quick.setPlacement(MenuPlacement.HEADER);
        MenuItem newPatient = open("quick.patient", quick, role);
        newPatient.setPlacement(MenuPlacement.QUICK);

        List<MenuItem> all = List.of(home, settings, general, org, quick, newPatient);
        List<MenuEntry> tree = build(all, all.toArray(MenuItem[]::new));

        assertThat(MenuTreeBuilder.forPlacement(tree, "LEFT")).extracting(MenuEntry::getId).containsExactly("home", "settings");
        List<MenuEntry> hub = MenuTreeBuilder.forPlacement(tree, "SETTINGS");
        assertThat(hub).extracting(MenuEntry::getId).containsExactly("settings.general");
        assertThat(hub.get(0).getChildren()).extracting(MenuEntry::getId).containsExactly("settings.organisation");
        assertThat(MenuTreeBuilder.forPlacement(tree, "QUICK")).extracting(MenuEntry::getId).containsExactly("quick.patient");
        assertThat(MenuTreeBuilder.forPlacement(tree, "TILE")).isEmpty();
    }

    @Test
    void forPlacementSeesPruningOfTheWholeTree() {
        MenuItem settings = open("settings", null, role);
        MenuItem staff = open("settings.staff", settings, role);
        staff.setPlacement(MenuPlacement.SETTINGS);
        // settings itself is hidden from the caller, so its pages are not reachable through the hub
        List<MenuEntry> tree = build(List.of(settings, staff), staff);
        assertThat(MenuTreeBuilder.forPlacement(tree, "SETTINGS")).isEmpty();
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
    void parentWithRouteStaysWhenNoChildIsVisible() {
        MenuItem admin = open("admin", null, role);          // has its own route
        MenuItem users = open("users", admin, role);
        assertThat(build(List.of(admin, users), admin)).extracting(MenuEntry::getId).containsExactly("admin");
    }

    @Test
    void routelessParentWithNoVisibleChildIsPruned() {
        MenuItem admin = open("admin", null, role);
        admin.setRoute(null);
        MenuItem users = open("users", admin, role);
        assertThat(build(List.of(admin, users), admin)).isEmpty();
    }

    @Test
    void childrenOutsideNavigationDoNotMakeAParentPrunable() {
        MenuItem orders = open("orders", null, role);
        MenuItem detail = open("orders.detail", orders, role);   // an inner screen: never in navigation
        detail.setIsVisible(false);
        detail.setPlacement(MenuPlacement.INNER);
        assertThat(build(List.of(orders, detail), orders)).extracting(MenuEntry::getId).containsExactly("orders");
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
        MenuItem a = open("a", null, role); a.setRoute(null);
        MenuItem b = open("b", a, role); b.setRoute(null);
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
        CatalogOverride o = new CatalogOverride("Bee", null, 0, false);
        List<MenuEntry> tree = MenuTreeBuilder.build(List.of(CatalogItem.of(a), CatalogItem.of(b)), ids(a, b), Map.of(b.getId(), o), "en");
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
        MenuItem p = open("p", null, role); p.setRoute(null);
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

    private MenuItem temp(String code, MenuItem parent, int sort, int priority) {
        MenuItem t = open(code, parent, role);
        t.setSource(MenuSource.TEMPORARY);
        t.setSortOrder(sort);
        t.setPriority(priority);
        t.setBadge("New");
        t.setEndsAt(java.time.Instant.parse("2030-01-01T00:00:00Z"));
        return t;
    }

    @Test
    void leafParentSurvivesWhenItsOnlyChildIsATemporaryMenuTheCallerCannotSee() {
        MenuItem parent = open("parent", null, role);
        MenuItem campaign = temp("campaign", parent, 0, 0);
        List<MenuEntry> tree = build(List.of(parent, campaign), parent);      // campaign not visible
        assertThat(tree).extracting(MenuEntry::getId).containsExactly("parent");
        assertThat(tree.get(0).getChildren()).isNull();
    }

    @Test
    void permanentParentWithOnlyHiddenPermanentChildrenIsStillPrunedEvenIfATemporaryChildExists() {
        MenuItem parent = open("parent", null, role); parent.setRoute(null);
        MenuItem kid = open("kid", parent, role);
        MenuItem campaign = temp("campaign", parent, 0, 0);
        assertThat(build(List.of(parent, kid, campaign), parent)).isEmpty();
    }

    @Test
    void temporaryEntriesCarryBadgeAndExpiryAndOrderByPriority() {
        MenuItem low  = temp("low", null, 5, 1);
        MenuItem high = temp("high", null, 5, 9);
        MenuItem perm = open("perm", null, role); perm.setSortOrder(5);
        List<MenuEntry> tree = build(List.of(low, high, perm), low, high, perm);
        assertThat(tree).extracting(MenuEntry::getId).containsExactly("high", "low", "perm");
        assertThat(tree.get(0).getBadge()).isEqualTo("New");
        assertThat(tree.get(0).getExpiresAt()).isEqualTo(java.time.Instant.parse("2030-01-01T00:00:00Z"));
        assertThat(tree.get(2).getExpiresAt()).isNull();
    }
}
