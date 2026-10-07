package com.is2c.hrm.audit.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import com.is2c.hrm.common.domain.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLog {

    @Id
    private UUID id = Ids.newId();

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "user_id")
    private UUID userId;

    private String username;

    @Column(name = "on_behalf_of")
    private UUID onBehalfOf;

    @Column(nullable = false)
    private String action;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id")
    private String entityId;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "before_json")
    private String beforeJson;

    @Column(name = "after_json")
    private String afterJson;

    private String reason;

    private String ip;

    @Column(name = "correlation_id")
    private String correlationId;

    protected AuditLog() {
    }

    public AuditLog(Instant occurredAt, UUID userId, String username, UUID onBehalfOf, String action,
            String entityType, String entityId, UUID companyId, String beforeJson, String afterJson, String reason,
            String ip, String correlationId) {
        this.occurredAt = occurredAt;
        this.userId = userId;
        this.username = username;
        this.onBehalfOf = onBehalfOf;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.companyId = companyId;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.reason = reason;
        this.ip = ip;
        this.correlationId = correlationId;
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public UUID getOnBehalfOf() {
        return onBehalfOf;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getBeforeJson() {
        return beforeJson;
    }

    public String getAfterJson() {
        return afterJson;
    }

    public String getReason() {
        return reason;
    }

    public String getIp() {
        return ip;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
