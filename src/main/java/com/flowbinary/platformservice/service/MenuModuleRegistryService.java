package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.config.AppProperties;
import com.flowbinary.platformservice.dto.module.ManifestHistoryResponse;
import com.flowbinary.platformservice.dto.module.ModuleRequest;
import com.flowbinary.platformservice.dto.module.ModuleResponse;
import com.flowbinary.platformservice.exception.MenuConflictException;
import com.flowbinary.platformservice.exception.MenuDraftException;
import com.flowbinary.platformservice.model.*;
import com.flowbinary.platformservice.repository.ApplicationRepository;
import com.flowbinary.platformservice.repository.MenuItemRepository;
import com.flowbinary.platformservice.repository.MenuModuleManifestRepository;
import com.flowbinary.platformservice.repository.MenuModuleRepository;
import com.flowbinary.platformservice.repository.PrivilegeRepository;
import com.flowbinary.platformservice.service.MenuAdminAccess.AdminContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The operator-facing lifecycle of a module: register, approve, suspend, resume,
 * deregister. Platform operators only — a module cannot register, approve or remove itself.
 *
 * <pre>
 *   PENDING --approve--> ACTIVE <--resume-- SUSPENDED
 *                          |  \--suspend------^
 *                          +--deregister--> DEREGISTERED (code stays reserved)
 * </pre>
 * Suspending hides the module's items without losing them; deregistering removes them.
 */
@Service
@RequiredArgsConstructor
public class MenuModuleRegistryService {

    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_-]{1,31}$");

    private final MenuModuleRepository         modules;
    private final MenuModuleManifestRepository history;
    private final MenuItemRepository           items;
    private final ApplicationRepository        applications;
    private final PrivilegeRepository          privileges;
    private final AuditService                 audit;
    private final MenuCatalogEvents            events;
    private final MenuMetrics                  metrics;
    private final AppProperties                properties;

    @Transactional
    public ModuleResponse register(AdminContext ctx, ModuleRequest req) {
        operatorOnly(ctx);
        if (req.code() == null || !CODE.matcher(req.code()).matches()) {
            throw new MenuDraftException(-1, "code must be 2-32 chars: a lowercase letter, then lowercase letters, digits, '_' or '-'");
        }
        if (req.ownerClientId() == null || req.ownerClientId().isBlank()) {
            throw new MenuDraftException(-1, "ownerClientId is required");
        }
        if (modules.existsByCode(req.code())) throw new MenuConflictException("Module code '" + req.code() + "' is already registered");
        if (properties.getMenu().getModules().getReservedCodes().contains(req.code())) {
            throw new MenuConflictException("Module code '" + req.code() + "' is reserved for a platform menu area");
        }
        if (modules.existsLiveClient(req.ownerClientId())) {
            throw new MenuConflictException("Client '" + req.ownerClientId() + "' already owns a module");
        }
        Application app = applications.findByCode(req.application() == null ? MenuResolutionService.DEFAULT_APPLICATION : req.application())
                .orElseThrow(() -> new MenuDraftException(-1, "Unknown application"));

        if (items.existsNonModuleInNamespace(app.getId(), req.code(), req.code() + ".")) {
            throw new MenuConflictException("Module code '" + req.code() + "' is already used by platform or tenant menu items");
        }

        List<String> prefixes = normalisePrefixes(req.allowedRoutePrefixes());
        checkPrefixes(prefixes, app, null);
        checkPrivilegeModule(req.privilegeModuleCode());
        checkUrl(req.manifestUrl());

        MenuModule m = modules.save(MenuModule.builder()
                .code(req.code()).name(req.name()).description(req.description()).application(app)
                .ownerClientId(req.ownerClientId()).manifestUrl(blankToNull(req.manifestUrl()))
                .allowedRoutePrefixes(prefixes).privilegeModuleCode(blankToNull(req.privilegeModuleCode()))
                .createdBy(ctx.actorId()).updatedBy(ctx.actorId()).build());
        record(ctx, "MENU_MODULE_REGISTERED", m, null);
        return ModuleResponse.of(m);
    }

    /** Name, description, manifest URL, route prefixes and privilege namespace. Takes effect on the next manifest. */
    @Transactional
    public ModuleResponse update(AdminContext ctx, String code, ModuleRequest req) {
        operatorOnly(ctx);
        MenuModule m = lock(code);
        if (m.getStatus() == ModuleStatus.DEREGISTERED) throw new MenuConflictException("Module is deregistered");
        Map<String, Object> before = summary(m);

        List<String> prefixes = normalisePrefixes(req.allowedRoutePrefixes());
        checkPrefixes(prefixes, m.getApplication(), m.getId());
        checkPrivilegeModule(req.privilegeModuleCode());
        checkUrl(req.manifestUrl());

        m.setName(req.name());
        m.setDescription(req.description());
        m.setManifestUrl(blankToNull(req.manifestUrl()));
        m.setAllowedRoutePrefixes(prefixes);
        m.setPrivilegeModuleCode(blankToNull(req.privilegeModuleCode()));
        m.setUpdatedBy(ctx.actorId());
        record(ctx, "MENU_MODULE_UPDATED", m, before);
        return ModuleResponse.of(m);
    }

    @Transactional
    public ModuleResponse approve(AdminContext ctx, String code) {
        return transition(ctx, code, "MENU_MODULE_APPROVED", Set.of(ModuleStatus.PENDING), ModuleStatus.ACTIVE);
    }

    @Transactional
    public ModuleResponse suspend(AdminContext ctx, String code) {
        return transition(ctx, code, "MENU_MODULE_SUSPENDED", Set.of(ModuleStatus.ACTIVE), ModuleStatus.SUSPENDED);
    }

    @Transactional
    public ModuleResponse resume(AdminContext ctx, String code) {
        return transition(ctx, code, "MENU_MODULE_RESUMED", Set.of(ModuleStatus.SUSPENDED), ModuleStatus.ACTIVE);
    }

    @Transactional
    public ModuleResponse deregister(AdminContext ctx, String code) {
        return transition(ctx, code, "MENU_MODULE_DEREGISTERED",
                Set.of(ModuleStatus.PENDING, ModuleStatus.ACTIVE, ModuleStatus.SUSPENDED), ModuleStatus.DEREGISTERED);
    }

    @Transactional(readOnly = true)
    public List<ModuleResponse> list(AdminContext ctx) {
        operatorOnly(ctx);
        return modules.findAllByOrderByCodeAsc().stream().map(ModuleResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public ModuleResponse get(AdminContext ctx, String code) {
        operatorOnly(ctx);
        return ModuleResponse.of(modules.findByCode(code).orElseThrow(() -> new NoSuchElementException("Module not found")));
    }

    @Transactional(readOnly = true)
    public List<ManifestHistoryResponse> history(AdminContext ctx, String code, int limit) {
        operatorOnly(ctx);
        MenuModule m = modules.findByCode(code).orElseThrow(() -> new NoSuchElementException("Module not found"));
        return history.findByModuleIdOrderByReceivedAtDesc(m.getId(), PageRequest.of(0, Math.min(Math.max(limit, 1), 200)))
                .stream().map(ManifestHistoryResponse::of).toList();
    }

    // ── State machine ──────────────────────────────────────────────────────

    private ModuleResponse transition(AdminContext ctx, String code, String action, Set<ModuleStatus> from, ModuleStatus to) {
        operatorOnly(ctx);
        MenuModule m = lock(code);
        if (!from.contains(m.getStatus())) {
            throw new MenuConflictException("Module '" + code + "' is " + m.getStatus() + "; cannot go to " + to);
        }
        Map<String, Object> before = summary(m);
        m.setStatus(to);
        m.setUpdatedBy(ctx.actorId());

        List<MenuItem> own = items.findGlobalItems(m.getApplication().getId()).stream()
                .filter(i -> i.isModule() && i.getModule() != null && i.getModule().getId().equals(m.getId()))
                .toList();
        switch (to) {
            case SUSPENDED -> own.forEach(i -> i.setLifecycleStatus(MenuLifecycle.DISABLED));
            case ACTIVE -> own.forEach(i -> i.setLifecycleStatus(MenuLifecycle.ACTIVE));
            case DEREGISTERED -> {
                Instant now = Instant.now();
                own.forEach(i -> {
                    if (i.getDeletedAt() == null) i.setDeletedAt(now);
                });
                m.setHealth(ModuleHealth.UNKNOWN);
                m.setNextAttemptAt(null);
            }
            default -> { }
        }
        if (to == ModuleStatus.ACTIVE && m.getManifestUrl() != null) m.setNextAttemptAt(Instant.now());   // first pull promptly
        items.flush();
        metrics.gauge("menu.module.health", MenuModuleSyncService.healthValue(m.getHealth()), "module", code);
        record(ctx, action, m, before);
        if (!own.isEmpty()) events.changed(m.getApplication().getCode(), null);
        return ModuleResponse.of(m);
    }

    // ── Validation ─────────────────────────────────────────────────────────

    private static List<String> normalisePrefixes(List<String> raw) {
        if (raw == null) return List.of();
        return raw.stream().map(p -> p == null ? null : p.trim().toLowerCase()).distinct().toList();
    }

    private void checkPrefixes(List<String> prefixes, Application app, UUID selfId) {
        List<String> errors = new ArrayList<>(MenuManifestValidator.checkPrefixes(prefixes));
        if (errors.isEmpty()) {
            for (MenuModule other : modules.findLiveByApplication(app.getId())) {
                if (other.getId().equals(selfId)) continue;
                for (String mine : prefixes) {
                    for (String theirs : other.getAllowedRoutePrefixes()) {
                        if (MenuManifestValidator.prefixesOverlap(mine, theirs)) {
                            errors.add("prefix '" + mine + "' overlaps '" + theirs + "' of module '" + other.getCode() + "'");
                        }
                    }
                }
            }
        }
        if (!errors.isEmpty()) throw new MenuDraftException(-1, String.join("; ", errors));
    }

    private void checkPrivilegeModule(String moduleCode) {
        if (moduleCode == null || moduleCode.isBlank()) return;
        boolean known = privileges.findByIsActiveTrueOrderByCategoryAscCodeAsc().stream()
                .anyMatch(p -> moduleCode.equals(p.getModuleCode()));
        if (!known) throw new MenuDraftException(-1, "privilegeModuleCode '" + moduleCode + "' matches no privilege");
    }

    private void checkUrl(String url) {
        if (url == null || url.isBlank()) return;
        AppProperties.MenuProperties.ModuleProperties cfg = properties.getMenu().getModules();
        ManifestUrlPolicy.check(url, cfg.isAllowPrivateHosts(), cfg.isAllowInsecureHttp(), MenuModuleRegistryService::resolve)
                .ifPresent(reason -> {
                    throw new MenuDraftException(-1, reason);
                });
    }

    static InetAddress[] resolve(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static void operatorOnly(AdminContext ctx) {
        if (!ctx.scope().isGlobal() || !ctx.isOperator()) throw new AccessDeniedException("Only platform operators manage modules");
    }

    private MenuModule lock(String code) {
        return modules.findByCodeForUpdate(code).orElseThrow(() -> new NoSuchElementException("Module not found"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static Map<String, Object> summary(MenuModule m) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("status", m.getStatus().name());
        a.put("name", m.getName());
        a.put("ownerClientId", m.getOwnerClientId());
        a.put("manifestUrl", m.getManifestUrl());
        a.put("allowedRoutePrefixes", m.getAllowedRoutePrefixes());
        a.put("privilegeModuleCode", m.getPrivilegeModuleCode());
        return a;
    }

    private void record(AdminContext ctx, String action, MenuModule m, Map<String, Object> before) {
        audit.record(new AuditService.Entry(null, ctx.actorId(), action, "MENU_MODULE", m.getCode(), before, summary(m), null, "API"));
    }
}
