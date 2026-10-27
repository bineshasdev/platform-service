package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.dto.module.MenuManifest;
import com.flowbinary.platformservice.model.*;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Decides whether a module's manifest may become part of the platform menu.
 * All-or-nothing: any error rejects the whole manifest, so a module can never
 * leave the menu half-updated. Every error is reported, not just the first.
 *
 * <p>What a module can and cannot do is enforced here, not trusted:
 * <ul>
 *   <li>only its own namespace — codes are {@code <module>.<key>}, parents are
 *       items of the same manifest, so it cannot attach to or shadow anyone else's items;</li>
 *   <li>only its own URL space — every route sits under a prefix an operator registered,
 *       is an in-app path, and is not already used by another owner;</li>
 *   <li>only privileges it may reference — they must exist, and belong to the module's
 *       privilege namespace or be shared platform privileges;</li>
 *   <li>never public — every LINK must declare a privilege, since that is its only gate;</li>
 *   <li>no external links, no roles, no audience: who holds the privilege is the platform's business.</li>
 * </ul>
 */
public final class MenuManifestValidator {

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ITEMS = 200;

    static final Pattern KEY    = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,39}$");
    static final Pattern LOCALE = Pattern.compile("^[a-z]{2,3}(-[A-Za-z0-9]{2,8})?$");
    static final Pattern PREFIX = Pattern.compile("^/[a-z0-9][a-z0-9/_-]*[a-z0-9_-]$|^/[a-z0-9]$");

    /** Lookups outside the manifest. */
    public interface Refs {
        Optional<Privilege> privilege(String code);

        boolean featureExists(String code);

        /** Who owns a live global item with this route, other than the module being validated; empty if free. */
        Optional<String> routeOwner(String route);

        /** Who owns an item with this code, other than the module being validated; empty if free. */
        Optional<String> codeOwner(String code);
    }

    /** One accepted item, normalised and ready to apply. */
    public record ValidItem(String key, String code, String parentKey, String label, String description, String icon,
                            String route, MenuItemType type, MenuBehavior behavior, MenuTarget target,
                            MenuPlacement placement, List<String> tenantTypes, int order,
                            boolean separatorBefore, Privilege privilege, String feature,
                            Map<String, String> translations) {
    }

    public record Result(List<String> errors, List<ValidItem> items) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    private MenuManifestValidator() {
    }

    public static Result validate(MenuModule module, MenuManifest m, int maxDepth, Refs refs) {
        List<String> errors = new ArrayList<>();
        if (m == null) return new Result(List.of("manifest is empty"), List.of());

        if (m.schemaVersion() != SCHEMA_VERSION) errors.add("schemaVersion must be " + SCHEMA_VERSION);
        if (!module.getCode().equals(m.module())) errors.add("module must be '" + module.getCode() + "'");
        if (m.version() == null || m.version().isBlank() || m.version().length() > 64) {
            errors.add("version is required (at most 64 characters)");
        }
        List<MenuManifest.Item> raw = m.items() == null ? List.of() : m.items();
        if (raw.size() > MAX_ITEMS) errors.add("a manifest may declare at most " + MAX_ITEMS + " items");

        Map<String, Integer> indexByKey = new LinkedHashMap<>();
        List<ValidItem> items = new ArrayList<>();
        Set<String> routes = new HashSet<>();

        for (int i = 0; i < Math.min(raw.size(), MAX_ITEMS); i++) {
            MenuManifest.Item it = raw.get(i);
            String at = "items[" + i + "]";
            if (it == null) {
                errors.add(at + " is empty");
                continue;
            }
            if (it.key() == null || !KEY.matcher(it.key()).matches()) {
                errors.add(at + ".key must be 1-40 chars of lowercase letters, digits, '_' and '-'");
                continue;
            }
            if (indexByKey.putIfAbsent(it.key(), i) != null) {
                errors.add(at + ".key '" + it.key() + "' is used more than once");
                continue;
            }
            String code = module.getCode() + "." + it.key();
            if (code.length() > 63) errors.add(at + ".key makes the item code longer than 63 characters");
            refs.codeOwner(code).ifPresent(o -> errors.add(at + ": code '" + code + "' already belongs to " + o));

            ValidItem v = item(module, it, at, code, errors, routes, refs);
            if (v != null) items.add(v);
        }

        checkTree(raw, indexByKey, maxDepth, errors);
        return new Result(errors, errors.isEmpty() ? items : List.of());
    }

    private static ValidItem item(MenuModule module, MenuManifest.Item it, String at, String code, List<String> errors,
                                  Set<String> routes, Refs refs) {
        int before = errors.size();

        if (it.label() == null || it.label().isBlank() || it.label().length() > 100) {
            errors.add(at + ".label is required (at most 100 characters)");
        }
        if (it.description() != null && it.description().length() > 500) errors.add(at + ".description is longer than 500 characters");
        if (it.icon() != null && !MenuOpValidator.ICON.matcher(it.icon()).matches()) errors.add(at + ".icon has invalid characters");

        MenuItemType type = MenuItemType.LINK;
        if (it.type() != null) {
            try {
                type = MenuItemType.valueOf(it.type());
            } catch (IllegalArgumentException e) {
                errors.add(at + ".type '" + it.type() + "' is unknown");
            }
        }
        if (type == MenuItemType.EXTERNAL) errors.add(at + ".type EXTERNAL is not allowed for modules");

        MenuBehavior behavior = type == MenuItemType.GROUP ? MenuBehavior.EXPAND : MenuBehavior.NAVIGATE;
        if (it.behavior() != null) {
            try {
                behavior = MenuBehavior.valueOf(it.behavior());
            } catch (IllegalArgumentException e) {
                errors.add(at + ".behavior '" + it.behavior() + "' is unknown");
            }
        }
        MenuTarget target = MenuTarget.SELF;
        if (it.target() != null) {
            try {
                target = MenuTarget.valueOf(it.target());
            } catch (IllegalArgumentException e) {
                errors.add(at + ".target '" + it.target() + "' is unknown");
            }
        }
        MenuPlacement placement = MenuPlacement.LEFT;
        if (it.placement() != null) {
            try {
                placement = MenuPlacement.valueOf(it.placement());
            } catch (IllegalArgumentException e) {
                errors.add(at + ".placement '" + it.placement() + "' is unknown");
            }
        }
        List<String> tenantTypes = null;
        if (it.tenantTypes() != null && !it.tenantTypes().isEmpty()) {
            TreeSet<String> types = new TreeSet<>();
            for (String t : it.tenantTypes()) {
                try {
                    types.add(TenantType.valueOf(t).name());
                } catch (IllegalArgumentException | NullPointerException e) {
                    errors.add(at + ".tenantTypes '" + t + "' is unknown");
                }
            }
            tenantTypes = List.copyOf(types);
        }
        if (it.order() != null && (it.order() < -10_000 || it.order() > 10_000)) errors.add(at + ".order must be between -10000 and 10000");

        // route
        String route = it.route();
        if (type == MenuItemType.LINK && route == null) errors.add(at + ".route is required for a LINK");
        if (type == MenuItemType.DIVIDER && route != null) errors.add(at + ".route is not allowed on a DIVIDER");
        if (route != null && type != MenuItemType.DIVIDER) {
            if (!MenuOpValidator.ROUTE.matcher(route).matches()) {
                errors.add(at + ".route must be an in-app path starting with a single '/'");
            } else if (!underPrefix(route, module.getAllowedRoutePrefixes())) {
                errors.add(at + ".route '" + route + "' is outside this module's registered prefixes "
                        + module.getAllowedRoutePrefixes());
            } else {
                if (!routes.add(route)) errors.add(at + ".route '" + route + "' is used by another item of this manifest");
                refs.routeOwner(route).ifPresent(o -> errors.add(at + ".route '" + route + "' is already used by " + o));
            }
        }

        // privilege: the only gate a module item has
        Privilege privilege = null;
        if (it.requiredPrivilege() != null && !it.requiredPrivilege().isBlank()) {
            privilege = refs.privilege(it.requiredPrivilege()).orElse(null);
            if (privilege == null) {
                errors.add(at + ".requiredPrivilege '" + it.requiredPrivilege() + "' does not exist");
            } else if (privilege.getModuleCode() != null
                    && !privilege.getModuleCode().equals(module.getPrivilegeModuleCode())) {
                errors.add(at + ".requiredPrivilege '" + it.requiredPrivilege() + "' belongs to another module");
            }
        } else if (type == MenuItemType.LINK) {
            errors.add(at + ".requiredPrivilege is required on a LINK: it is the only thing that decides who sees it");
        }
        if (it.requiredFeature() != null && !it.requiredFeature().isBlank() && !refs.featureExists(it.requiredFeature())) {
            errors.add(at + ".requiredFeature '" + it.requiredFeature() + "' does not exist");
        }

        // translations
        Map<String, String> translations = new LinkedHashMap<>();
        if (it.translations() != null) {
            if (it.translations().size() > 20) errors.add(at + ".translations has more than 20 locales");
            it.translations().forEach((locale, label) -> {
                if (!LOCALE.matcher(locale).matches()) errors.add(at + ".translations: bad locale '" + locale + "'");
                else if (label == null || label.isBlank() || label.length() > 100) errors.add(at + ".translations[" + locale + "] is empty or too long");
                else translations.put(locale.toLowerCase(), label);
            });
        }

        if (errors.size() > before) return null;
        return new ValidItem(it.key(), code, it.parent(), it.label(), it.description(), it.icon(), route, type, behavior,
                target, placement, tenantTypes, it.order() == null ? 0 : it.order(), Boolean.TRUE.equals(it.separatorBefore()), privilege,
                it.requiredFeature() == null || it.requiredFeature().isBlank() ? null : it.requiredFeature(), translations);
    }

    /** Parents exist, are not dividers, there is no cycle, and no branch is too deep. */
    private static void checkTree(List<MenuManifest.Item> raw, Map<String, Integer> indexByKey, int maxDepth, List<String> errors) {
        Map<String, String> parentOf = new HashMap<>();
        for (Map.Entry<String, Integer> e : indexByKey.entrySet()) {
            MenuManifest.Item it = raw.get(e.getValue());
            if (it.parent() == null) continue;
            String at = "items[" + e.getValue() + "].parent";
            Integer p = indexByKey.get(it.parent());
            if (p == null) {
                errors.add(at + " '" + it.parent() + "' is not an item of this manifest");
            } else if ("DIVIDER".equals(raw.get(p).type())) {
                errors.add(at + " '" + it.parent() + "' is a DIVIDER and cannot have children");
            } else {
                parentOf.put(e.getKey(), it.parent());
            }
        }
        for (String key : parentOf.keySet()) {
            int depth = 0;
            Set<String> seen = new HashSet<>();
            String cur = key;
            while (parentOf.containsKey(cur)) {
                if (!seen.add(cur)) {
                    errors.add("items[" + indexByKey.get(key) + "]: parent chain of '" + key + "' contains a cycle");
                    depth = -1;
                    break;
                }
                cur = parentOf.get(cur);
                depth++;
            }
            if (depth >= maxDepth) {
                errors.add("items[" + indexByKey.get(key) + "]: '" + key + "' is " + (depth + 1)
                        + " levels deep; the maximum is " + maxDepth);
            }
        }
    }

    static boolean underPrefix(String route, List<String> prefixes) {
        String path = route;
        int cut = path.length();
        for (char c : new char[]{'?', '#'}) {
            int i = path.indexOf(c);
            if (i >= 0) cut = Math.min(cut, i);
        }
        path = path.substring(0, cut);
        for (String p : prefixes) {
            if (path.equals(p) || path.startsWith(p + "/")) return true;
        }
        return false;
    }

    /** Registration-time check of a module's route prefixes. */
    public static List<String> checkPrefixes(List<String> prefixes) {
        List<String> errors = new ArrayList<>();
        if (prefixes == null || prefixes.isEmpty()) return List.of("at least one route prefix is required");
        if (prefixes.size() > 10) errors.add("at most 10 route prefixes");
        for (String p : prefixes) {
            if (p == null || !PREFIX.matcher(p).matches()) {
                errors.add("route prefix '" + p + "' must start with '/', use lowercase letters, digits, '/', '_' or '-', and not end with '/'");
            }
        }
        return errors;
    }

    /** True when one prefix is the other or lies beneath it, on a path-segment boundary. */
    public static boolean prefixesOverlap(String a, String b) {
        return a.equals(b) || a.startsWith(b + "/") || b.startsWith(a + "/");
    }
}
