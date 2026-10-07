package com.is2c.hrm.audit.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.is2c.hrm.audit.domain.AuditLog;
import com.is2c.hrm.audit.domain.AuditLogRepository;
import com.is2c.hrm.common.web.CorrelationId;
import com.is2c.hrm.security.api.AuthenticatedUser;
import com.is2c.hrm.security.api.CurrentUser;

import tools.jackson.databind.json.JsonMapper;

/**
 * Appends audit records (FR-008). Called inside the business transaction so the audit row commits or rolls back
 * together with the change it describes.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final JsonMapper json;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, JsonMapper json, Clock clock) {
        this.repository = repository;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public void record(AuditEntry entry) {
        AuthenticatedUser user = CurrentUser.find().orElse(null);
        record(entry, user == null ? null : user.id(), user == null ? null : user.username());
    }

    /** For events without an authenticated principal (e.g. login attempts). */
    @Transactional
    public void record(AuditEntry entry, UUID userId, String username) {
        repository.save(new AuditLog(clock.instant(), userId, username, entry.onBehalfOf(), entry.action(),
                entry.entityType(), entry.entityId(), entry.companyId(), toJson(entry.before()),
                toJson(entry.after()), entry.reason(), clientIp(), CorrelationId.current()));
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof String s ? s : json.writeValueAsString(value);
    }

    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();
        }
        return null;
    }
}
