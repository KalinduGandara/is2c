package com.is2c.hrm.audit.service;

import java.util.UUID;

/**
 * One critical change. {@code before}/{@code after} are snapshots (records or maps) serialized to JSON.
 * Use {@link #of(String, String, Object)} and the {@code with*} methods.
 */
public record AuditEntry(String action, String entityType, String entityId, UUID companyId, Object before,
        Object after, String reason, UUID onBehalfOf) {

    public static AuditEntry of(String action, String entityType, Object entityId) {
        return new AuditEntry(action, entityType, entityId == null ? null : entityId.toString(), null, null, null,
                null, null);
    }

    public AuditEntry company(UUID companyId) {
        return new AuditEntry(action, entityType, entityId, companyId, before, after, reason, onBehalfOf);
    }

    public AuditEntry before(Object before) {
        return new AuditEntry(action, entityType, entityId, companyId, before, after, reason, onBehalfOf);
    }

    public AuditEntry after(Object after) {
        return new AuditEntry(action, entityType, entityId, companyId, before, after, reason, onBehalfOf);
    }

    public AuditEntry reason(String reason) {
        return new AuditEntry(action, entityType, entityId, companyId, before, after, reason, onBehalfOf);
    }

    public AuditEntry onBehalfOf(UUID onBehalfOf) {
        return new AuditEntry(action, entityType, entityId, companyId, before, after, reason, onBehalfOf);
    }
}
