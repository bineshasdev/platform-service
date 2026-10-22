package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.exception.MenuDraftException;
import com.flowbinary.platformservice.model.*;

import java.util.regex.Pattern;

/**
 * Field-level checks on a draft operation, independent of tree state.
 * Tenant admins author menu content, so this is also where injection is
 * refused: routes must be in-app paths (or https for EXTERNAL), icons and
 * codes are restricted to a safe alphabet.
 */
public final class MenuOpValidator {

    static final Pattern CODE  = Pattern.compile("^[a-z0-9][a-z0-9_.-]{0,62}$");
    static final Pattern ICON  = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    static final Pattern ROUTE = Pattern.compile("^/(?!/)[A-Za-z0-9/_\\-.~%:@!$&'()*+,;=\\[\\]?#]*$");
    static final Pattern HTTPS = Pattern.compile("^https://[^\\s<>\"']+$");

    private MenuOpValidator() {
    }

    public static void validate(int index, MenuOp op, MenuScope scope) {
        if (op.op() == null) throw new MenuDraftException(index, "op is required");
        if (op.op() != MenuOpType.REORDER) requireCode(index, op.code());

        switch (op.op()) {
            case PUT_ITEM -> {
                requireText(index, "label", op.label(), 100);
                content(index, op);
            }
            case PATCH_ITEM -> {
                if (op.label() != null) requireText(index, "label", op.label(), 100);
                content(index, op);
            }
            case MOVE_ITEM -> {
                if (op.parentCode() != null) requireCode(index, op.parentCode());
            }
            case REORDER -> {
                if (op.parentCode() != null) requireCode(index, op.parentCode());
                if (op.orderedCodes() == null || op.orderedCodes().isEmpty()) {
                    throw new MenuDraftException(index, "orderedCodes is required");
                }
                if (op.orderedCodes().size() != op.orderedCodes().stream().distinct().count()) {
                    throw new MenuDraftException(index, "orderedCodes contains duplicates");
                }
                op.orderedCodes().forEach(c -> requireCode(index, c));
            }
            case SET_ENABLED -> {
                if (op.enabled() == null) throw new MenuDraftException(index, "enabled is required");
            }
            case DELETE_ITEM -> { }
            case SET_ROLES -> {
                if (op.roles() == null) throw new MenuDraftException(index, "roles is required (may be empty)");
            }
            case SET_USER_ACCESS -> {
                if (scope.isGlobal()) {
                    throw new MenuDraftException(index, "User access is managed in a tenant scope, not the global menu");
                }
                if (op.userId() == null) throw new MenuDraftException(index, "userId is required");
                if (op.effect() != null && !op.effect().equals("GRANT") && !op.effect().equals("DENY")) {
                    throw new MenuDraftException(index, "effect must be GRANT, DENY or null to clear");
                }
            }
            case SET_OVERRIDE -> {
                if (scope.isGlobal()) {
                    throw new MenuDraftException(index, "Overrides exist only in a tenant scope");
                }
                if (op.label() != null) requireText(index, "label", op.label(), 100);
                if (op.icon() != null && !ICON.matcher(op.icon()).matches()) {
                    throw new MenuDraftException(index, "icon has invalid characters");
                }
            }
        }
    }

    private static void content(int index, MenuOp op) {
        if (op.icon() != null && !ICON.matcher(op.icon()).matches()) {
            throw new MenuDraftException(index, "icon has invalid characters");
        }
        if (op.description() != null && op.description().length() > 500) {
            throw new MenuDraftException(index, "description is longer than 500 characters");
        }
        if (op.parentCode() != null) requireCode(index, op.parentCode());
        enumValue(index, "type", op.type(), MenuItemType.class);
        enumValue(index, "behavior", op.behavior(), MenuBehavior.class);
        enumValue(index, "target", op.target(), MenuTarget.class);
        if (op.plans() != null) {
            for (String p : op.plans()) enumValue(index, "plans", p, TenantPlan.class);
        }
        if (op.route() != null) {
            boolean external = MenuItemType.EXTERNAL.name().equals(op.type());
            boolean ok = external ? HTTPS.matcher(op.route()).matches() : ROUTE.matcher(op.route()).matches();
            if (!ok) {
                throw new MenuDraftException(index, external
                        ? "EXTERNAL routes must be https:// URLs"
                        : "route must be an in-app path starting with a single '/'");
            }
        }
    }

    private static void requireCode(int index, String code) {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new MenuDraftException(index,
                    "code must be 1-63 chars of lowercase letters, digits, '_', '.', '-' and start with a letter or digit");
        }
    }

    private static void requireText(int index, String field, String value, int max) {
        if (value == null || value.isBlank()) throw new MenuDraftException(index, field + " is required");
        if (value.length() > max) throw new MenuDraftException(index, field + " is longer than " + max + " characters");
    }

    private static <E extends Enum<E>> void enumValue(int index, String field, String value, Class<E> type) {
        if (value == null) return;
        try {
            Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new MenuDraftException(index, field + " has unknown value '" + value + "'");
        }
    }
}
