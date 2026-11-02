package com.flowbinary.platformservice.authz.service;

import com.flowbinary.platformservice.authz.model.AuthzPrivilege;
import com.flowbinary.platformservice.authz.model.RoleTemplate;
import com.flowbinary.platformservice.authz.model.TemplateProposal;
import com.flowbinary.platformservice.authz.repository.AuthzManifestRepository;
import com.flowbinary.platformservice.authz.repository.AuthzPrivilegeRepository;
import com.flowbinary.platformservice.authz.repository.RoleTemplateRepository;
import com.flowbinary.platformservice.authz.repository.TemplateProposalRepository;
import com.flowbinary.platformservice.service.AuditService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** A platform operator's decisions on the template grants services propose (design §21.1, P4-4). */
@Service
@RequiredArgsConstructor
public class TemplateProposalService {

    /** A proposal with the template it concerns spelled out. */
    public record ProposalView(UUID id, String service, String tenantType, String template, String privilege,
                               String status, Instant proposedAt, Instant decidedAt, String reason) {
    }

    public record ServiceSummary(String service, long privileges, long deprecated, long pendingProposals,
                                 Instant lastManifestAt, String lastVersion) {
    }

    private final TemplateProposalRepository proposals;
    private final RoleTemplateRepository templates;
    private final AuthzPrivilegeRepository privileges;
    private final AuthzManifestRepository manifests;
    private final AuthzEvents events;
    private final AuditService audit;
    private final EntityManager em;

    @Transactional(readOnly = true)
    public List<ProposalView> list(String status) {
        List<TemplateProposal> found = status == null || status.isBlank()
                ? proposals.findAllByOrderByProposedAtDesc() : proposals.findByStatusOrderByProposedAtAsc(status);
        Map<UUID, RoleTemplate> byId = templates.findAll().stream().collect(Collectors.toMap(RoleTemplate::getId, t -> t));
        return found.stream().map(p -> view(p, byId.get(p.getTemplateId()))).toList();
    }

    /** Adds the privilege to the template. Everyone holding a role derived from it gets it, so the caches are cleared. */
    @Transactional
    public ProposalView approve(UUID id, UUID actor) {
        TemplateProposal p = pending(id);
        RoleTemplate t = templates.findById(p.getTemplateId()).orElseThrow();
        AuthzPrivilege priv = privileges.findById(p.getPrivilegeCode()).orElseThrow();
        if (!priv.isActive()) throw new IllegalStateException("The privilege is deprecated; reject the proposal instead");
        t.getPrivileges().add(p.getPrivilegeCode());
        t.setVersion(t.getVersion() + 1);
        templates.save(t);
        em.flush();   // the database also checks that the privilege exists for the template's tenant type
        decide(p, "APPROVED", actor, null);
        audit.record(new AuditService.Entry(null, actor, "AUTHZ_PROPOSAL_APPROVED", "authz_template", t.getId().toString(),
                null, Map.of("privilege", p.getPrivilegeCode(), "templateVersion", t.getVersion()), null, "API"));
        events.catalogueChanged("TEMPLATE_CHANGED");
        return view(p, t);
    }

    @Transactional
    public ProposalView reject(UUID id, UUID actor, String reason) {
        TemplateProposal p = pending(id);
        decide(p, "REJECTED", actor, reason);
        audit.record(new AuditService.Entry(null, actor, "AUTHZ_PROPOSAL_REJECTED", "authz_template", p.getTemplateId().toString(),
                null, Map.of("privilege", p.getPrivilegeCode()), reason, "API"));
        return view(p, templates.findById(p.getTemplateId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<ServiceSummary> services() {
        Map<String, List<AuthzPrivilege>> byService = privileges.findAll().stream()
                .collect(Collectors.groupingBy(AuthzPrivilege::getOwnerService, TreeMap::new, Collectors.toList()));
        Map<String, Long> pending = proposals.findByStatusOrderByProposedAtAsc("PENDING").stream()
                .collect(Collectors.groupingBy(TemplateProposal::getService, Collectors.counting()));
        List<ServiceSummary> out = new ArrayList<>();
        byService.forEach((service, list) -> {
            var last = manifests.findFirstByServiceAndOutcomeOrderByReceivedAtDesc(service, "ACCEPTED");
            out.add(new ServiceSummary(service, list.size(), list.stream().filter(p -> !p.isActive()).count(),
                    pending.getOrDefault(service, 0L), last.map(m -> m.getReceivedAt()).orElse(null),
                    last.map(m -> m.getVersion()).orElse(null)));
        });
        return out;
    }

    private TemplateProposal pending(UUID id) {
        TemplateProposal p = proposals.findById(id).orElseThrow(() -> new NoSuchElementException("Proposal not found: " + id));
        if (!"PENDING".equals(p.getStatus())) throw new IllegalStateException("Proposal is already " + p.getStatus());
        return p;
    }

    private void decide(TemplateProposal p, String status, UUID actor, String reason) {
        p.setStatus(status);
        p.setDecidedBy(actor);
        p.setDecidedAt(Instant.now());
        p.setReason(reason);
        proposals.save(p);
    }

    private static ProposalView view(TemplateProposal p, RoleTemplate t) {
        return new ProposalView(p.getId(), p.getService(), t == null ? null : t.getTenantType(), t == null ? null : t.getCode(),
                p.getPrivilegeCode(), p.getStatus(), p.getProposedAt(), p.getDecidedAt(), p.getReason());
    }
}
