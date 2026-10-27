package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.dto.menu.GroupRequest;
import com.flowbinary.platformservice.dto.menu.GroupResponse;
import com.flowbinary.platformservice.dto.menu.MemberResponse;
import com.flowbinary.platformservice.exception.MenuConflictException;
import com.flowbinary.platformservice.model.TenantUser;
import com.flowbinary.platformservice.model.UserGroup;
import com.flowbinary.platformservice.model.UserGroupMember;
import com.flowbinary.platformservice.repository.MenuGroupAccessRepository;
import com.flowbinary.platformservice.repository.TenantUserRepository;
import com.flowbinary.platformservice.repository.UserGroupMemberRepository;
import com.flowbinary.platformservice.repository.UserGroupRepository;
import com.flowbinary.platformservice.service.MenuAdminAccess.AdminContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * A tenant's user groups and their members. Groups are live data, not part of
 * the menu draft: membership changes apply immediately (like assigning a role
 * to a user) and are audited. What a group may <em>see</em> is a menu
 * assignment and therefore goes through the draft.
 *
 * <p>Always tenant-scoped: members must belong to the group's tenant, and a
 * tenant can neither see nor reference another tenant's groups.
 */
@Service
@RequiredArgsConstructor
public class UserGroupService {

    public static final String MANAGE = "group.manage";

    private final UserGroupRepository       groups;
    private final UserGroupMemberRepository members;
    private final MenuGroupAccessRepository groupAccess;
    private final TenantUserRepository      tenantUsers;
    private final AuditService              audit;

    @Transactional(readOnly = true)
    public List<GroupResponse> list(AdminContext ctx) {
        authorise(ctx);
        return groups.findByTenantIdAndApplicationIdOrderByNameAsc(ctx.scope().tenantId(), appId(ctx)).stream()
                .map(g -> GroupResponse.of(g, members.countByGroupId(g.getId())))
                .toList();
    }

    @Transactional
    public GroupResponse create(AdminContext ctx, GroupRequest req) {
        authorise(ctx);
        if (req.code() == null || !MenuOpValidator.CODE.matcher(req.code()).matches()) {
            throw new IllegalArgumentException("code must be 1-63 chars of lowercase letters, digits, '_', '.', '-'");
        }
        if (groups.findByTenantIdAndApplicationIdAndCode(ctx.scope().tenantId(), appId(ctx), req.code()).isPresent()) {
            throw new MenuConflictException("Group '" + req.code() + "' already exists");
        }
        UserGroup g = groups.save(UserGroup.builder()
                .tenant(ctx.scope().tenant()).application(ctx.scope().application())
                .code(req.code()).name(req.name()).description(req.description())
                .isActive(req.active() == null || req.active())
                .createdBy(ctx.actorId()).updatedBy(ctx.actorId()).build());
        record(ctx, "CREATE", "USER_GROUP", g.getCode(), null, snapshot(g));
        return GroupResponse.of(g, 0);
    }

    @Transactional
    public GroupResponse update(AdminContext ctx, String code, GroupRequest req) {
        authorise(ctx);
        UserGroup g = require(ctx, code);
        Map<String, Object> before = snapshot(g);
        g.setName(req.name());
        g.setDescription(req.description());
        if (req.active() != null) g.setIsActive(req.active());
        g.setUpdatedBy(ctx.actorId());
        record(ctx, "UPDATE", "USER_GROUP", code, before, snapshot(g));
        return GroupResponse.of(g, members.countByGroupId(g.getId()));
    }

    /** Refused while any menu assignment references the group: removing those is a menu change and must be published. */
    @Transactional
    public void delete(AdminContext ctx, String code) {
        authorise(ctx);
        UserGroup g = require(ctx, code);
        if (groupAccess.existsByGroupId(g.getId())) {
            throw new MenuConflictException("Group '" + code + "' is used by menu assignments; remove those in a draft first");
        }
        Map<String, Object> before = snapshot(g);
        groups.delete(g);
        record(ctx, "DELETE", "USER_GROUP", code, before, null);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> members(AdminContext ctx, String code) {
        authorise(ctx);
        UserGroup g = require(ctx, code);
        return members.findByGroupIdWithUser(g.getId()).stream()
                .map(m -> toResponse(m.getUser()))
                .sorted(Comparator.comparing(MemberResponse::email))
                .toList();
    }

    @Transactional
    public List<MemberResponse> addMembers(AdminContext ctx, String code, Set<UUID> userIds) {
        authorise(ctx);
        UserGroup g = require(ctx, code);
        for (UUID id : userIds) {
            TenantUser u = tenantUsers.findById(id)
                    .filter(x -> x.getTenant().getId().equals(ctx.scope().tenantId()))
                    .orElseThrow(() -> new NoSuchElementException("User not found in this tenant"));
            if (members.findByGroupIdAndUserId(g.getId(), u.getId()).isPresent()) continue;
            members.save(UserGroupMember.builder().group(g).user(u).addedBy(ctx.actorId()).build());
            record(ctx, "ADD_MEMBER", "USER_GROUP_MEMBER", code + ":" + u.getId(), null, Map.of("userId", u.getId().toString()));
        }
        return members(ctx, code);
    }

    @Transactional
    public void removeMember(AdminContext ctx, String code, UUID userId) {
        authorise(ctx);
        UserGroup g = require(ctx, code);
        UserGroupMember m = members.findByGroupIdAndUserId(g.getId(), userId)
                .orElseThrow(() -> new NoSuchElementException("Not a member"));
        members.delete(m);
        record(ctx, "REMOVE_MEMBER", "USER_GROUP_MEMBER", code + ":" + userId, Map.of("userId", userId.toString()), null);
    }

    // ── internals ──────────────────────────────────────────────────────────

    private static void authorise(AdminContext ctx) {
        if (ctx.scope().isGlobal()) throw new AccessDeniedException("Groups belong to a tenant");
        ctx.require(MANAGE);
    }

    private static UUID appId(AdminContext ctx) {
        return ctx.scope().application().getId();
    }

    private UserGroup require(AdminContext ctx, String code) {
        return groups.findByTenantIdAndApplicationIdAndCode(ctx.scope().tenantId(), appId(ctx), code)
                .orElseThrow(() -> new NoSuchElementException("Group not found"));
    }

    private static Map<String, Object> snapshot(UserGroup g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", g.getCode());
        m.put("name", g.getName());
        m.put("description", g.getDescription());
        m.put("active", g.getIsActive());
        return m;
    }

    private static MemberResponse toResponse(TenantUser u) {
        String name = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                + (u.getLastName() == null ? "" : u.getLastName())).trim();
        return new MemberResponse(u.getId(), u.getEmail(), u.getUsername(), name.isEmpty() ? null : name);
    }

    private void record(AdminContext ctx, String action, String type, String id, Object before, Object after) {
        audit.record(new AuditService.Entry(ctx.scope().tenantId(), ctx.actorId(), action, type, id, before, after, null, "API"));
    }
}
