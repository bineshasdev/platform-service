package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.authz.repository.AuthzPrivilegeRepository;
import com.flowbinary.platformservice.authz.model.AuthzPrivilege;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowbinary.platformservice.config.AppProperties;
import com.flowbinary.platformservice.dto.module.MenuManifest;
import com.flowbinary.platformservice.exception.MenuConflictException;
import com.flowbinary.platformservice.model.*;
import com.flowbinary.platformservice.repository.*;
import com.flowbinary.platformservice.service.MenuManifestValidator.ValidItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Turns a module's manifest into platform menu items.
 *
 * <p><b>Materialised, not proxied.</b> Items are written to {@code menu_items} (global,
 * namespaced {@code <module>.<key>}); a page load never calls a module. A rejected manifest,
 * an unreachable module or a dead one therefore changes nothing: the last accepted menu
 * stays in place and the module is marked STALE or DOWN.
 *
 * <p><b>What a sync owns.</b> Content the module declares (label, icon, route, type,
 * privilege, feature, parent, translations) is overwritten on each accepted manifest.
 * What administrators decide is never touched: enabled/hidden, sort position after
 * creation, tenant overrides, and user/group denies. An item the module drops is
 * soft-deleted and is restored, admin settings intact, if the module declares it again.
 *
 * <p>Every delivery — accepted, rejected or failed — leaves a history row and an audit entry.
 */
@Slf4j
@Service
public class MenuModuleSyncService {

    /** Strict on purpose: an unknown field is a typo that could have been a restriction. */
    private static final ObjectMapper STRICT = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    public record SyncResult(String module, ManifestOutcome outcome, String version, boolean changed,
                             int created, int updated, int removed, List<String> errors) {
        public boolean accepted() {
            return outcome == ManifestOutcome.ACCEPTED;
        }
    }

    private final MenuModuleRepository         modules;
    private final MenuModuleManifestRepository history;
    private final MenuItemRepository           items;
    private final AuthzPrivilegeRepository     privileges;
    private final FeatureRepository            features;
    private final MenuHierarchyValidator       hierarchy;
    private final AuditService                 audit;
    private final MenuCatalogEvents            events;
    private final MenuMetrics                  metrics;
    private final AppProperties                properties;
    private final TransactionTemplate          tx;

    public MenuModuleSyncService(MenuModuleRepository modules, MenuModuleManifestRepository history,
                                 MenuItemRepository items, AuthzPrivilegeRepository privileges, FeatureRepository features,
                                 MenuHierarchyValidator hierarchy, AuditService audit, MenuCatalogEvents events,
                                 MenuMetrics metrics, AppProperties properties, PlatformTransactionManager txm) {
        this.modules = modules;
        this.history = history;
        this.items = items;
        this.privileges = privileges;
        this.features = features;
        this.hierarchy = hierarchy;
        this.audit = audit;
        this.events = events;
        this.metrics = metrics;
        this.properties = properties;
        this.tx = new TransactionTemplate(txm);
    }

    // ── Accepting a manifest ───────────────────────────────────────────────

    /**
     * @param via      PUSH or PULL
     * @param clientId the authenticated client for a push, null for a pull
     * @throws java.util.NoSuchElementException if the module does not exist
     * @throws MenuConflictException            if the module is not ACTIVE
     */
    public SyncResult accept(String moduleCode, String rawJson, String via, String clientId) {
        return metrics.observe("menu.module.sync", () -> {
            SyncResult result;
            try {
                result = tx.execute(s -> doAccept(moduleCode, rawJson, via, clientId));
            } catch (NoSuchElementException | MenuConflictException e) {
                throw e;
            } catch (RuntimeException e) {
                // The attempt rolled back whole; leave a trace of it and keep serving the last good menu.
                log.error("Applying manifest of module {} failed: {}", moduleCode, e.getMessage(), e);
                String msg = "could not apply manifest: " + e.getClass().getSimpleName() + ": " + e.getMessage();
                result = tx.execute(s -> recordFailure(moduleCode, via, clientId, ManifestOutcome.REJECTED, null, null, null, List.of(msg)));
            }
            metrics.count("menu.module.sync", "module", moduleCode, "outcome", result.outcome().name());
            return result;
        }, "module", moduleCode, "via", via);
    }

    private SyncResult doAccept(String moduleCode, String raw, String via, String clientId) {
        MenuModule module = modules.findByCodeForUpdate(moduleCode)
                .orElseThrow(() -> new NoSuchElementException("Module not found"));
        if (module.getStatus() != ModuleStatus.ACTIVE) {
            throw new MenuConflictException("Module '" + moduleCode + "' is " + module.getStatus() + "; it cannot publish a menu");
        }

        JsonNode tree;
        MenuManifest manifest;
        try {
            tree = STRICT.readTree(raw);
            manifest = STRICT.treeToValue(tree, MenuManifest.class);
        } catch (Exception e) {
            return fail(module, via, clientId, null, null, null, List.of("malformed manifest: " + firstLine(e.getMessage())));
        }
        String hash = sha256(canonical(tree));

        if (hash.equals(module.getCurrentHash())) {
            touchSuccess(module);
            return new SyncResult(moduleCode, ManifestOutcome.ACCEPTED, module.getCurrentVersion(), false, 0, 0, 0, List.of());
        }

        MenuManifestValidator.Result checked = MenuManifestValidator.validate(module, manifest,
                hierarchy.maxDepth(), refs(module));
        if (!checked.ok()) {
            return fail(module, via, clientId, tree, hash, manifest.version(), checked.errors());
        }

        int[] counts = apply(module, checked.items());

        Instant now = Instant.now();
        module.setManifest(tree);
        module.setCurrentHash(hash);
        module.setCurrentVersion(manifest.version());
        touchSuccess(module);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("created", counts[0]);
        summary.put("updated", counts[1]);
        summary.put("removed", counts[2]);
        history.save(MenuModuleManifest.builder().module(module).receivedAt(now).receivedVia(via).clientId(clientId)
                .outcome(ManifestOutcome.ACCEPTED).version(manifest.version()).contentHash(hash).manifest(tree)
                .summary(STRICT.valueToTree(summary)).build());
        audit.record(new AuditService.Entry(null, null, "MENU_MODULE_MANIFEST_ACCEPTED", "MENU_MODULE", moduleCode,
                null, Map.of("version", manifest.version(), "created", counts[0], "updated", counts[1], "removed", counts[2]),
                null, via, via.equals("PUSH") ? "SERVICE" : "SYSTEM", metadata(clientId, via)));

        boolean changed = counts[0] + counts[1] + counts[2] > 0;
        if (changed) events.changed(module.getApplication().getCode(), null);
        return new SyncResult(moduleCode, ManifestOutcome.ACCEPTED, manifest.version(), changed,
                counts[0], counts[1], counts[2], List.of());
    }

    // ── Applying ───────────────────────────────────────────────────────────

    /** @return {created, updated, removed} */
    private int[] apply(MenuModule module, List<ValidItem> valid) {
        List<MenuItem> all = new ArrayList<>(items.findGlobalItems(module.getApplication().getId()));
        Map<String, MenuItem> byCode = new HashMap<>();
        all.forEach(m -> byCode.put(m.getCode(), m));
        List<MenuItem> live = new ArrayList<>(all.stream().filter(m -> m.getDeletedAt() == null).toList());

        int created = 0, updated = 0;
        Set<String> declared = new HashSet<>();

        for (ValidItem v : parentsFirst(valid)) {
            declared.add(v.code());
            MenuItem item = byCode.get(v.code());
            boolean isNew = item == null;
            boolean changed;

            if (isNew) {
                item = MenuItem.builder().application(module.getApplication()).tenant(null).module(module)
                        .source(MenuSource.MODULE).code(v.code()).label(v.label()).lifecycleStatus(MenuLifecycle.ACTIVE)
                        .sortOrder(v.order()).build();
                all.add(item);
                byCode.put(v.code(), item);
                changed = true;
            } else {
                changed = item.getDeletedAt() != null || differs(item, v);
                item.setDeletedAt(null);
            }
            if (changed) setContent(item, v);

            if (isNew) live.add(item); else if (!live.contains(item)) live.add(item);
            MenuItem parent = v.parentKey() == null ? null : byCode.get(module.getCode() + "." + v.parentKey());
            boolean reparent = isNew || !sameParent(item, parent);
            if (reparent) hierarchy.place(item, parent, live);

            syncTranslations(item, v.translations());
            if (isNew) {
                setPlans(item);
                created++;
                items.save(item);
            } else if (changed || reparent) {
                updated++;
            }
        }

        // anything the module no longer declares leaves the menu, but keeps its row and admin settings
        int removed = 0;
        Instant now = Instant.now();
        for (MenuItem m : all) {
            if (m.isModule() && m.getModule() != null && m.getModule().getId().equals(module.getId())
                    && m.getDeletedAt() == null && !declared.contains(m.getCode())) {
                m.setDeletedAt(now);
                removed++;
            }
        }
        items.flush();
        return new int[]{created, updated, removed};
    }

    private static void setContent(MenuItem item, ValidItem v) {
        item.setLabel(v.label());
        item.setDescription(v.description());
        item.setIcon(v.icon());
        item.setRoute(v.route());
        item.setType(v.type());
        item.setBehavior(v.behavior());
        item.setTarget(v.target());
        item.setPlacement(v.placement());
        item.setTenantTypes(v.tenantTypes() == null ? null : new ArrayList<>(v.tenantTypes()));
        item.setSeparatorBefore(v.separatorBefore());
        item.setRequiredPrivilege(v.privilege() == null ? null : v.privilege().getCode());
        item.setRequiredFeature(v.feature());
        // is_active, sort_order and lifecycle are the administrators' and the module state's, never the manifest's
    }

    private static boolean differs(MenuItem m, ValidItem v) {
        return !Objects.equals(m.getLabel(), v.label()) || !Objects.equals(m.getDescription(), v.description())
                || !Objects.equals(m.getIcon(), v.icon()) || !Objects.equals(m.getRoute(), v.route())
                || m.getType() != v.type() || m.getBehavior() != v.behavior() || m.getTarget() != v.target()
                || m.getPlacement() != v.placement() || !Objects.equals(m.getTenantTypes(), v.tenantTypes())
                || !Objects.equals(m.getSeparatorBefore(), v.separatorBefore())
                || !Objects.equals(m.getRequiredPrivilege(), v.privilege() == null ? null : v.privilege().getCode())
                || !Objects.equals(m.getRequiredFeature(), v.feature());
    }

    private static boolean sameParent(MenuItem item, MenuItem parent) {
        if (item.getParent() == null || parent == null) return item.getParent() == parent;
        return Objects.equals(item.getParent().getCode(), parent.getCode());
    }

    private static void syncTranslations(MenuItem item, Map<String, String> wanted) {
        Map<String, String> have = item.getTranslations().stream()
                .collect(Collectors.toMap(t -> t.getId().getLocale(), MenuItemTranslation::getLabel, (a, b) -> a));
        if (have.equals(wanted)) return;
        item.getTranslations().clear();
        wanted.forEach((locale, label) -> item.getTranslations().add(MenuItemTranslation.builder()
                .id(new MenuItemTranslationId(item.getId(), locale)).menuItem(item).label(label).build()));
    }

    private static void setPlans(MenuItem item) {
        for (TenantPlan p : TenantPlan.values()) {
            item.getPlanAccess().add(MenuPlanAccess.builder().id(new MenuPlanAccessId(item.getId(), p)).menuItem(item).build());
        }
    }

    /** Parents before children, so each item can be placed under an already-placed parent. */
    private static List<ValidItem> parentsFirst(List<ValidItem> valid) {
        Map<String, ValidItem> byKey = valid.stream().collect(Collectors.toMap(ValidItem::key, v -> v));
        List<ValidItem> out = new ArrayList<>();
        Set<String> done = new HashSet<>();
        for (ValidItem v : valid) visit(v, byKey, done, out);
        return out;
    }

    private static void visit(ValidItem v, Map<String, ValidItem> byKey, Set<String> done, List<ValidItem> out) {
        if (!done.add(v.key())) return;
        if (v.parentKey() != null && byKey.containsKey(v.parentKey())) visit(byKey.get(v.parentKey()), byKey, done, out);
        out.add(v);
    }

    // ── Failure bookkeeping ────────────────────────────────────────────────

    /** Records a pull that could not even deliver a manifest (timeout, refused, bad status). */
    public void recordPullFailure(String moduleCode, String message) {
        tx.executeWithoutResult(s -> recordFailure(moduleCode, "PULL", null, ManifestOutcome.FAILED, null, null, null, List.of(message)));
        metrics.count("menu.module.sync", "module", moduleCode, "outcome", ManifestOutcome.FAILED.name());
    }

    private SyncResult fail(MenuModule module, String via, String clientId, JsonNode tree, String hash, String version, List<String> errors) {
        return recordFailure(module.getCode(), via, clientId, ManifestOutcome.REJECTED, tree, hash, version, errors);
    }

    private SyncResult recordFailure(String moduleCode, String via, String clientId, ManifestOutcome outcome,
                                     JsonNode tree, String hash, String version, List<String> errors) {
        MenuModule module = modules.findByCodeForUpdate(moduleCode).orElseThrow(() -> new NoSuchElementException("Module not found"));
        AppProperties.MenuProperties.ModuleProperties cfg = properties.getMenu().getModules();
        Instant now = Instant.now();

        int failures = module.getConsecutiveFailures() + 1;
        module.setConsecutiveFailures(failures);
        module.setLastAttemptAt(now);
        module.setLastError(String.join("; ", errors.stream().limit(3).toList()));
        module.setHealth(module.getCurrentHash() == null || failures >= cfg.getFailuresBeforeDown()
                ? ModuleHealth.DOWN : ModuleHealth.STALE);
        module.setNextAttemptAt(module.getManifestUrl() == null ? null : now.plus(backoff(failures, cfg)));
        metrics.gauge("menu.module.health", healthValue(module.getHealth()), "module", moduleCode);

        ArrayNode errorNode = STRICT.createArrayNode();
        errors.forEach(errorNode::add);
        history.save(MenuModuleManifest.builder().module(module).receivedAt(now).receivedVia(via).clientId(clientId)
                .outcome(outcome).version(version).contentHash(hash).manifest(tree).errors(errorNode).build());

        // A long outage must not flood the audit trail: record the start of a streak and the move to DOWN.
        if (failures == 1 || failures == cfg.getFailuresBeforeDown()) {
            audit.record(new AuditService.Entry(null, null, "MENU_MODULE_MANIFEST_" + outcome.name(), "MENU_MODULE", moduleCode,
                    null, Map.of("errors", errors.stream().limit(20).toList(), "consecutiveFailures", failures),
                    null, via, via.equals("PUSH") ? "SERVICE" : "SYSTEM", metadata(clientId, via)));
        }
        log.warn("Module {} manifest {} ({} in a row): {}", moduleCode, outcome, failures, module.getLastError());
        return new SyncResult(moduleCode, outcome, version, false, 0, 0, 0, errors);
    }

    private void touchSuccess(MenuModule module) {
        AppProperties.MenuProperties.ModuleProperties cfg = properties.getMenu().getModules();
        Instant now = Instant.now();
        module.setLastSyncedAt(now);
        module.setLastAttemptAt(now);
        module.setConsecutiveFailures(0);
        module.setLastError(null);
        module.setHealth(ModuleHealth.UP);
        module.setNextAttemptAt(module.getManifestUrl() == null ? null : now.plus(cfg.getPullInterval()));
        metrics.gauge("menu.module.health", healthValue(ModuleHealth.UP), "module", module.getCode());
    }

    static Duration backoff(int failures, AppProperties.MenuProperties.ModuleProperties cfg) {
        long factor = 1L << Math.min(Math.max(failures - 1, 0), 20);
        Duration d = cfg.getBackoffBase().multipliedBy(factor);
        return d.compareTo(cfg.getPullInterval()) > 0 ? cfg.getPullInterval() : d;
    }

    static double healthValue(ModuleHealth h) {
        return switch (h) {
            case UP -> 2;
            case STALE -> 1;
            case DOWN -> 0;
            case UNKNOWN -> -1;
        };
    }

    private static Map<String, Object> metadata(String clientId, String via) {
        Map<String, Object> m = new HashMap<>();
        m.put("via", via);
        if (clientId != null) m.put("clientId", clientId);
        return m;
    }

    // ── References for validation ──────────────────────────────────────────

    private MenuManifestValidator.Refs refs(MenuModule module) {
        UUID moduleId = module.getId();
        UUID appId = module.getApplication().getId();
        List<MenuItem> global = items.findGlobalItems(appId);
        return new MenuManifestValidator.Refs() {
            @Override
            public Optional<AuthzPrivilege> privilege(String code) {
                return privileges.findById(code).filter(AuthzPrivilege::isActive);
            }

            @Override
            public boolean featureExists(String code) {
                return features.existsByCode(code);
            }

            @Override
            public Optional<String> routeOwner(String route) {
                return global.stream()
                        .filter(m -> m.getDeletedAt() == null && route.equals(m.getRoute()) && !ownedBy(m, moduleId))
                        .findFirst().map(MenuModuleSyncService::owner);
            }

            @Override
            public Optional<String> codeOwner(String code) {
                // soft-deleted rows keep their code, so they count too
                return global.stream()
                        .filter(m -> code.equals(m.getCode()) && !ownedBy(m, moduleId))
                        .findFirst().map(MenuModuleSyncService::owner);
            }
        };
    }

    private static boolean ownedBy(MenuItem m, UUID moduleId) {
        return m.isModule() && m.getModule() != null && m.getModule().getId().equals(moduleId);
    }

    private static String owner(MenuItem m) {
        return m.isModule() && m.getModule() != null ? "module '" + m.getModule().getCode() + "'" : "the platform menu";
    }

    // ── Canonical form / hash ──────────────────────────────────────────────

    /** Key-sorted rendering, so the same content hashes the same whatever order the module sends fields in. */
    static String canonical(JsonNode node) {
        StringBuilder sb = new StringBuilder();
        write(node, sb);
        return sb.toString();
    }

    private static void write(JsonNode n, StringBuilder sb) {
        if (n.isObject()) {
            sb.append('{');
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            n.fields().forEachRemaining(e -> sorted.put(e.getKey(), e.getValue()));
            boolean first = true;
            for (Map.Entry<String, JsonNode> e : sorted.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(STRICT.valueToTree(e.getKey()).toString()).append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
        } else if (n.isArray()) {
            sb.append('[');
            for (int i = 0; i < n.size(); i++) {
                if (i > 0) sb.append(',');
                write(n.get(i), sb);
            }
            sb.append(']');
        } else {
            sb.append(n.toString());
        }
    }

    static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String firstLine(String s) {
        if (s == null) return "invalid JSON";
        int i = s.indexOf('\n');
        String line = i < 0 ? s : s.substring(0, i);
        return line.length() > 300 ? line.substring(0, 300) : line;
    }
}
