package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.model.AuditLog;
import com.flowbinary.platformservice.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Writes the platform's append-only audit trail. {@link #record} joins the
 * caller's transaction, so an audited change and its audit row commit or
 * roll back together.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public record Entry(UUID tenantId, UUID actorId, String action, String entityType, String entityId,
                        Object before, Object after, String reason, String source,
                        String actorType, java.util.Map<String, Object> metadata) {

        public Entry(UUID tenantId, UUID actorId, String action, String entityType, String entityId,
                     Object before, Object after, String reason, String source) {
            this(tenantId, actorId, action, entityType, entityId, before, after, reason, source, null, null);
        }
    }

    @Transactional
    public void record(Entry e) {
        auditLogRepository.save(AuditLog.builder()
                .tenantId(e.tenantId())
                .actorId(e.actorId())
                .actorType(e.actorType() != null ? e.actorType() : e.actorId() == null ? "SYSTEM" : "USER")
                .action(e.action())
                .entityType(e.entityType())
                .entityId(e.entityId())
                .beforeState(e.before())
                .afterState(e.after())
                .reason(e.reason())
                .source(e.source() == null ? "API" : e.source())
                .correlationId(correlationId())
                .metadata(e.metadata() != null ? e.metadata() : new java.util.HashMap<>())
                .build());
    }

    /** {@code tenantId} null = unrestricted (platform operators only). */
    @Transactional(readOnly = true)
    public Page<AuditLog> search(UUID tenantId, String entityType, String entityId, String action,
                                 Instant from, Instant to, int page, int size) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (tenantId != null)   p.add(cb.equal(root.get("tenantId"), tenantId));
            if (entityType != null) p.add(cb.like(root.get("entityType"), entityType + "%"));
            if (entityId != null)   p.add(cb.equal(root.get("entityId"), entityId));
            if (action != null)     p.add(cb.equal(root.get("action"), action));
            p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            p.add(cb.lessThan(root.get("occurredAt"), to));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return auditLogRepository.findAll(spec,
                PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "occurredAt")));
    }

    private static String correlationId() {
        String id = MDC.get("traceId");
        if (id == null) id = MDC.get("correlationId");
        return id != null ? id : UUID.randomUUID().toString();
    }
}
