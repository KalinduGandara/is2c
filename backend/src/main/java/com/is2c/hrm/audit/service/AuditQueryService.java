package com.is2c.hrm.audit.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.audit.domain.AuditLog;
import com.is2c.hrm.audit.domain.AuditLogRepository;
import com.is2c.hrm.platform.export.TableData;
import com.is2c.hrm.platform.export.TableData.Column;
import com.is2c.hrm.security.api.AuthenticatedUser;
import com.is2c.hrm.security.api.CurrentUser;
import com.is2c.hrm.security.api.ScopeType;

import jakarta.persistence.criteria.Predicate;

@Service
public class AuditQueryService {

    /** Upper bound of rows in one export; narrow the filter for more. */
    public static final int MAX_EXPORT_ROWS = 10_000;

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditQueryService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public record Filter(Instant from, Instant to, UUID userId, String entityType, String entityId, String action,
            UUID companyId) {
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(Filter f, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "occurredAt"));
        return repository.findAll(spec(f, CurrentUser.get()), sorted);
    }

    /** FR-008 export: newest first, at most {@link #MAX_EXPORT_ROWS} rows, filters listed in the header. */
    @Transactional(readOnly = true)
    public TableData table(Filter f) {
        List<List<Object>> rows = new ArrayList<>();
        for (AuditLog a : search(f, PageRequest.of(0, MAX_EXPORT_ROWS))) {
            rows.add(java.util.Arrays.asList(TIME.format(a.getOccurredAt()), a.getUsername(), a.getAction(),
                    a.getEntityType(), a.getEntityId(), a.getReason(), a.getBeforeJson(), a.getAfterJson(), a.getIp()));
        }
        List<String> header = new ArrayList<>();
        if (f.from() != null || f.to() != null) {
            header.add("From " + (f.from() == null ? "…" : TIME.format(f.from())) + " to "
                    + (f.to() == null ? "…" : TIME.format(f.to())));
        }
        java.util.Map<String, Object> filters = new java.util.LinkedHashMap<>();
        filters.put("User", f.userId());
        filters.put("Entity", f.entityType());
        filters.put("Entity ID", f.entityId());
        filters.put("Action", f.action());
        filters.put("Company", f.companyId());
        filters.forEach((k, v) -> {
            if (v != null) {
                header.add(k + ": " + v);
            }
        });
        if (rows.size() == MAX_EXPORT_ROWS) {
            header.add("Limited to the newest " + MAX_EXPORT_ROWS + " entries");
        }
        return new TableData("Audit log", header, List.of(Column.text("Time (UTC)"), Column.text("User"),
                Column.text("Action"), Column.text("Entity"), Column.text("Entity ID"), Column.text("Reason"),
                Column.text("Before"), Column.text("After"), Column.text("IP")), rows, null, true,
                CurrentUser.get().username(), clock.instant());
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    static Specification<AuditLog> spec(Filter f, AuthenticatedUser user) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), f.from()));
            }
            if (f.to() != null) {
                p.add(cb.lessThan(root.get("occurredAt"), f.to()));
            }
            if (f.userId() != null) {
                p.add(cb.equal(root.get("userId"), f.userId()));
            }
            if (f.entityType() != null) {
                p.add(cb.equal(root.get("entityType"), f.entityType()));
            }
            if (f.entityId() != null) {
                p.add(cb.equal(root.get("entityId"), f.entityId()));
            }
            if (f.action() != null) {
                p.add(cb.equal(root.get("action"), f.action()));
            }
            if (f.companyId() != null) {
                p.add(cb.equal(root.get("companyId"), f.companyId()));
            }
            if (user.scopeType() != ScopeType.ALL) {
                // Non-global users only see entries tied to companies in their scope (FR-006).
                p.add(user.scopeCompanyIds().isEmpty() ? cb.disjunction()
                        : root.get("companyId").in(user.scopeCompanyIds()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
