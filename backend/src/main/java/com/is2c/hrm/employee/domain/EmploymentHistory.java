package com.is2c.hrm.employee.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import com.is2c.hrm.common.domain.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Employment lifecycle record (FR-017). Append-only. */
@Entity
@Immutable
@Table(name = "employment_history")
public class EmploymentHistory {

    public enum EventType { JOINED, TRANSFER, PROMOTION, STATUS_CHANGE, CHANGE }

    @Id
    private UUID id = Ids.newId();

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(name = "changes_json", nullable = false)
    private String changesJson;

    private String reason;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected EmploymentHistory() {
    }

    public EmploymentHistory(UUID employeeId, LocalDate effectiveDate, EventType eventType, String changesJson,
            String reason, UUID recordedBy, Instant recordedAt) {
        this.employeeId = employeeId;
        this.effectiveDate = effectiveDate;
        this.eventType = eventType;
        this.changesJson = changesJson;
        this.reason = reason;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public EventType getEventType() {
        return eventType;
    }

    public String getChangesJson() {
        return changesJson;
    }

    public String getReason() {
        return reason;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
