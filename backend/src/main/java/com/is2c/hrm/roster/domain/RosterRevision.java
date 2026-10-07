package com.is2c.hrm.roster.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import com.is2c.hrm.common.domain.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A change to a published roster cell (FR-039). Values are shift codes, "OFF", or null (empty). */
@Entity
@Immutable
@Table(name = "roster_revision")
public class RosterRevision {

    @Id
    private UUID id = Ids.newId();

    @Column(name = "roster_id", nullable = false)
    private UUID rosterId;

    @Column(nullable = false)
    private int revision;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(nullable = false)
    private String reason;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected RosterRevision() {
    }

    public RosterRevision(UUID rosterId, int revision, UUID employeeId, LocalDate workDate, String oldValue,
            String newValue, String reason, UUID changedBy, Instant changedAt) {
        this.rosterId = rosterId;
        this.revision = revision;
        this.employeeId = employeeId;
        this.workDate = workDate;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.reason = reason;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public int getRevision() {
        return revision;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getReason() {
        return reason;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
