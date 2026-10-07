package com.is2c.hrm.audit.service;

import java.time.Instant;
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
import com.is2c.hrm.security.api.AuthenticatedUser;
import com.is2c.hrm.security.api.CurrentUser;
import com.is2c.hrm.security.api.ScopeType;

import jakarta.persistence.criteria.Predicate;

@Service
public class AuditQueryService {

    private final AuditLogRepository repository;

    public AuditQueryService(AuditLogRepository repository) {
        this.repository = repository;
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
