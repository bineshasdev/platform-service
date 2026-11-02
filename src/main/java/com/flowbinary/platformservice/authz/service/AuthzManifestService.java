package com.flowbinary.platformservice.authz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowbinary.platformservice.authz.model.AuthzManifestRecord;
import com.flowbinary.platformservice.authz.model.AuthzPrivilege;
import com.flowbinary.platformservice.authz.model.RoleTemplate;
import com.flowbinary.platformservice.authz.model.TemplateProposal;
import com.flowbinary.platformservice.authz.repository.AuthzManifestRepository;
import com.flowbinary.platformservice.authz.repository.AuthzPrivilegeRepository;
import com.flowbinary.platformservice.authz.repository.RoleTemplateRepository;
import com.flowbinary.platformservice.authz.repository.TemplateProposalRepository;
import com.flowbinary.platformservice.authz.service.AuthzManifest.GrantDecl;
import com.flowbinary.platformservice.authz.service.AuthzManifest.PrivilegeDecl;
import com.flowbinary.platformservice.repository.FeatureRepository;
import com.flowbinary.platformservice.service.AuditService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Accepts a service's authorization manifest (design §21): creates and updates its privileges, deprecates the
 * ones it stopped declaring, and opens template-grant proposals for an operator. An invalid manifest changes
 * nothing and reports every problem; identical content is a no-op.
 */
@Service
@RequiredArgsConstructor
public class AuthzManifestService {

    public enum Outcome { ACCEPTED, UNCHANGED, REJECTED }

    public record Result(Outcome outcome, List<String> errors, List<String> created, List<String> updated,
                         List<String> deprecated, List<String> reactivated, int proposalsOpened) {
        static Result rejected(List<String> errors) {
            return new Result(Outcome.REJECTED, errors, List.of(), List.of(), List.of(), List.of(), 0);
        }

        static Result unchanged() {
            return new Result(Outcome.UNCHANGED, List.of(), List.of(), List.of(), List.of(), List.of(), 0);
        }
    }

    private final ObjectMapper json;
    private final AuthzPrivilegeRepository privileges;
    private final RoleTemplateRepository templates;
    private final TemplateProposalRepository proposals;
    private final AuthzManifestRepository history;
    private final FeatureRepository features;
    private final AuthzEvents events;
    private final AuditService audit;
    private final EntityManager em;

    @Transactional
    public Result accept(String service, String body, String clientId) {
        String hash = sha256(body);
        AuthzManifest manifest;
        try {
            manifest = json.readValue(body, AuthzManifest.class);
        } catch (Exception e) {
            return record(service, null, hash, clientId, Result.rejected(List.of("manifest is not valid: " + firstLine(e.getMessage()))));
        }

        Map<String, AuthzPrivilege> own = privileges.findByOwnerService(service).stream()
                .collect(Collectors.toMap(AuthzPrivilege::getCode, p -> p));
        List<String> errors = AuthzManifestValidator.validate(service, manifest, new AuthzManifestValidator.Context(own,
                features.findAll().stream().map(f -> f.getCode()).collect(Collectors.toSet()),
                templates.findAll().stream().map(t -> t.getTenantType() + "/" + t.getCode()).collect(Collectors.toSet())));
        if (!errors.isEmpty()) return record(service, manifest.version(), hash, clientId, Result.rejected(errors));

        boolean same = history.findFirstByServiceAndOutcomeOrderByReceivedAtDesc(service, "ACCEPTED")
                .map(r -> r.getContentHash().equals(hash)).orElse(false);
        if (same) return Result.unchanged();

        List<String> created = new ArrayList<>(), updated = new ArrayList<>(), reactivated = new ArrayList<>();
        Set<String> declared = new HashSet<>();
        for (PrivilegeDecl d : manifest.privileges()) {
            declared.add(d.code());
            AuthzPrivilege existing = own.get(d.code());
            if (existing == null) {
                privileges.save(toEntity(new AuthzPrivilege(), service, d));
                created.add(d.code());
            } else {
                boolean wasDeprecated = !existing.isActive();
                boolean changed = differs(existing, d);
                toEntity(existing, service, d);
                privileges.save(existing);
                if (wasDeprecated) reactivated.add(d.code());
                else if (changed) updated.add(d.code());
            }
        }
        List<String> deprecated = new ArrayList<>();
        for (AuthzPrivilege p : own.values()) {
            if (!declared.contains(p.getCode()) && p.isActive()) {
                p.setStatus("DEPRECATED");
                privileges.save(p);
                deprecated.add(p.getCode());
            }
        }
        em.flush();

        AuthzManifestRecord rec = saveRecord(service, manifest.version(), hash, clientId, "ACCEPTED", null, null);
        int opened = openProposals(service, manifest.templateGrants() == null ? List.of() : manifest.templateGrants(), rec);
        Result result = new Result(Outcome.ACCEPTED, List.of(), created, updated, deprecated, reactivated, opened);
        rec.setSummary(Map.of("created", created, "updated", updated, "deprecated", deprecated,
                "reactivated", reactivated, "proposalsOpened", opened));

        audit.record(new AuditService.Entry(null, null, "AUTHZ_MANIFEST_ACCEPTED", "authz_service", service,
                null, rec.getSummary(), null, "API", "SERVICE", Map.of("clientId", clientId)));
        if (!created.isEmpty() || !updated.isEmpty() || !deprecated.isEmpty() || !reactivated.isEmpty()) {
            events.catalogueChanged(AuthzEvents.CATALOGUE_CHANGED);
        }
        return result;
    }

    private int openProposals(String service, List<GrantDecl> grants, AuthzManifestRecord rec) {
        int opened = 0;
        for (GrantDecl g : grants) {
            RoleTemplate t = templates.findByTenantTypeAndCode(g.tenantType(), g.template()).orElseThrow();
            for (String code : g.privileges() == null ? List.<String>of() : g.privileges()) {
                if (t.getPrivileges().contains(code)) continue;
                if (proposals.findByTemplateIdAndPrivilegeCodeAndStatus(t.getId(), code, "PENDING").isPresent()) continue;
                TemplateProposal p = new TemplateProposal();
                p.setService(service);
                p.setTemplateId(t.getId());
                p.setPrivilegeCode(code);
                p.setManifestId(rec.getId());
                proposals.save(p);
                opened++;
            }
        }
        return opened;
    }

    private Result record(String service, String version, String hash, String clientId, Result rejected) {
        saveRecord(service, version, hash, clientId, "REJECTED", rejected.errors(), null);
        return rejected;
    }

    private AuthzManifestRecord saveRecord(String service, String version, String hash, String clientId, String outcome,
                                           List<String> errors, Map<String, Object> summary) {
        AuthzManifestRecord r = new AuthzManifestRecord();
        r.setService(service);
        r.setVersion(version);
        r.setContentHash(hash);
        r.setClientId(clientId);
        r.setOutcome(outcome);
        r.setErrors(errors);
        r.setSummary(summary);
        return history.save(r);
    }

    private static boolean differs(AuthzPrivilege e, PrivilegeDecl d) {
        return !Objects.equals(e.getName(), d.name())
                || !Objects.equals(e.getDescription(), d.description())
                || !new TreeSet<>(e.getTenantTypes()).equals(new TreeSet<>(d.tenantTypes()))
                || !Objects.equals(e.getFeatureCode(), blankToNull(d.feature()))
                || !Objects.equals(e.getScopeKind(), d.scope() == null ? "TENANT" : d.scope())
                || e.isPhi() != Boolean.TRUE.equals(d.phi())
                || e.isClinicalWrite() != Boolean.TRUE.equals(d.clinicalWrite())
                || !Objects.equals(e.getRisk(), d.risk() == null ? "LOW" : d.risk());
    }

    private static AuthzPrivilege toEntity(AuthzPrivilege p, String service, PrivilegeDecl d) {
        p.setCode(d.code());
        p.setOwnerService(service);
        p.setName(d.name());
        p.setDescription(d.description());
        p.setTenantTypes(d.tenantTypes().stream().sorted().toList());
        p.setFeatureCode(blankToNull(d.feature()));
        p.setScopeKind(d.scope() == null ? "TENANT" : d.scope());
        p.setPhi(Boolean.TRUE.equals(d.phi()));
        p.setClinicalWrite(Boolean.TRUE.equals(d.clinicalWrite()));
        p.setAssignableByTenant(true);
        p.setRisk(d.risk() == null ? "LOW" : d.risk());
        p.setStatus("ACTIVE");
        return p;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String firstLine(String s) {
        if (s == null) return "unreadable";
        int i = s.indexOf('\n');
        return (i < 0 ? s : s.substring(0, i)).trim();
    }

    private static String sha256(String body) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
