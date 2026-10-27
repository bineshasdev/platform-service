package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.exception.MenuHierarchyException;
import com.flowbinary.platformservice.exception.MenuHierarchyException.Reason;
import com.flowbinary.platformservice.model.Application;
import com.flowbinary.platformservice.model.MenuItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.flowbinary.platformservice.service.MenuTestFixtures.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MenuHierarchyValidatorTest {

    private final MenuHierarchyValidator validator = new MenuHierarchyValidator(4);

    private static Reason reasonOf(Runnable r) {
        try {
            r.run();
        } catch (MenuHierarchyException e) {
            return e.getReason();
        }
        return null;
    }

    @Test
    void newRootGetsDepthZeroAndCodePath() {
        MenuItem n = item("n", null);
        n.setId(null);
        validator.place(n, null, List.of());
        assertThat(n.getDepth()).isZero();
        assertThat(n.getPath()).isEqualTo("n");
    }

    @Test
    void childGetsParentPath() {
        MenuItem a = item("a", null);
        MenuItem n = item("n", null);
        n.setId(null);
        validator.place(n, a, List.of(a));
        assertThat(n.getDepth()).isEqualTo(1);
        assertThat(n.getPath()).isEqualTo("a/n");
        assertThat(n.getParent()).isSameAs(a);
    }

    @Test
    void fourthLevelAllowedFifthRejected() {
        MenuItem l0 = item("l0", null), l1 = item("l1", l0), l2 = item("l2", l1), l3 = item("l3", l2);
        MenuItem ok = item("ok", null); ok.setId(null);
        validator.place(ok, l2, List.of(l0, l1, l2, l3));            // level 4 (depth 3)
        MenuItem tooDeep = item("deep", null); tooDeep.setId(null);
        assertThat(reasonOf(() -> validator.place(tooDeep, l3, List.of(l0, l1, l2, l3))))
                .isEqualTo(Reason.MAX_DEPTH);
    }

    @Test
    void movingSubtreeThatWouldExceedDepthIsRejected() {
        MenuItem a = item("a", null), a1 = item("a1", a), a2 = item("a2", a1);   // 3 levels
        MenuItem x = item("x", null), x1 = item("x1", x), x2 = item("x2", x1);   // 3 levels
        List<MenuItem> all = List.of(a, a1, a2, x, x1, x2);
        assertThat(reasonOf(() -> validator.place(a, x2, all))).isEqualTo(Reason.MAX_DEPTH);
        assertThat(a.getParent()).isNull();                                       // untouched
    }

    @Test
    void movingSubtreeRecomputesDescendantDepthAndPath() {
        MenuItem a = item("a", null), a1 = item("a1", a);
        MenuItem x = item("x", null);
        validator.place(a, x, List.of(a, a1, x));
        assertThat(a.getPath()).isEqualTo("x/a");
        assertThat(a1.getDepth()).isEqualTo(2);
        assertThat(a1.getPath()).isEqualTo("x/a/a1");
    }

    @Test
    void cycleIsRejected() {
        MenuItem a = item("a", null), b = item("b", a), c = item("c", b);
        assertThat(reasonOf(() -> validator.place(a, c, List.of(a, b, c)))).isEqualTo(Reason.CYCLE);
    }

    @Test
    void selfParentIsRejected() {
        MenuItem a = item("a", null);
        assertThat(reasonOf(() -> validator.place(a, a, List.of(a)))).isEqualTo(Reason.SELF_PARENT);
    }

    @Test
    void crossApplicationParentIsRejected() {
        MenuItem a = item("a", null);
        MenuItem other = item("o", null);
        Application app2 = Application.builder().code("PATIENT_PORTAL").name("P").build();
        app2.setId(UUID.randomUUID());
        other.setApplication(app2);
        MenuItem n = item("n", null); n.setId(null);
        assertThat(reasonOf(() -> validator.place(n, other, List.of(a, other)))).isEqualTo(Reason.CROSS_APPLICATION);
    }

    @Test
    void duplicateCodeInApplicationIsRejected() {
        MenuItem a = item("dup", null);
        MenuItem n = item("dup", null); n.setId(null);
        assertThat(reasonOf(() -> validator.place(n, null, List.of(a)))).isEqualTo(Reason.DUPLICATE_CODE);
    }

    @Test
    void deletedParentIsRejected() {
        MenuItem a = item("a", null);
        a.setDeletedAt(java.time.Instant.now());
        MenuItem n = item("n", null); n.setId(null);
        assertThat(reasonOf(() -> validator.place(n, a, List.of(a)))).isEqualTo(Reason.PARENT_NOT_FOUND);
    }

    @Test
    void auditReportsOrphanAndTooDeep() {
        MenuItem ghost = item("ghost", null);
        MenuItem orphan = item("orphan", ghost);            // ghost not in the list
        MenuItem l0 = item("l0", null), l1 = item("l1", l0), l2 = item("l2", l1), l3 = item("l3", l2), l4 = item("l4", l3);
        List<String> problems = validator.audit(List.of(orphan, l0, l1, l2, l3, l4));
        assertThat(problems).anyMatch(p -> p.startsWith("Orphan 'orphan'"));
        assertThat(problems).anyMatch(p -> p.contains("'l4' is at depth 4"));
    }

    @Test
    void auditOfHealthyTreeIsEmpty() {
        MenuItem a = item("a", null), b = item("b", a);
        assertThat(validator.audit(List.of(a, b))).isEmpty();
    }

    @Test
    void invalidMaxDepthRejected() {
        assertThatThrownBy(() -> new MenuHierarchyValidator(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void temporaryMenuCannotBeAParent() {
        MenuItem t = item("t", null);
        t.setSource(com.flowbinary.platformservice.model.MenuSource.TEMPORARY);
        MenuItem n = item("n", null); n.setId(null);
        assertThat(reasonOf(() -> validator.place(n, t, List.of(t)))).isEqualTo(Reason.TEMPORARY_PARENT);
    }

    @Test
    void onlyAModulesOwnItemsMaySitUnderAModuleItem() {
        MenuItem parent = item("billing.root", null);
        parent.setSource(com.flowbinary.platformservice.model.MenuSource.MODULE);
        MenuItem platform = item("p", null); platform.setId(null);
        assertThat(reasonOf(() -> validator.place(platform, parent, List.of(parent)))).isEqualTo(Reason.MODULE_PARENT);

        MenuItem own = item("billing.child", null); own.setId(null);
        own.setSource(com.flowbinary.platformservice.model.MenuSource.MODULE);
        validator.place(own, parent, List.of(parent));                       // fine
        assertThat(own.getParent()).isSameAs(parent);

        MenuItem temp = item("t", null); temp.setId(null);
        temp.setSource(com.flowbinary.platformservice.model.MenuSource.TEMPORARY);
        validator.place(temp, parent, List.of(parent));                      // a campaign may sit there (it is a leaf and expires)
    }
}
