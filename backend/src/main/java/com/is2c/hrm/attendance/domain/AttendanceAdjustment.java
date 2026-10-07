package com.is2c.hrm.attendance.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.is2c.hrm.attendance.service.AttendanceCalculator.DayStatus;
import com.is2c.hrm.common.domain.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Manual attendance creation/correction with before/after values (FR-050..052). Raw events stay untouched. */
@Entity
@Table(name = "attendance_adjustment")
public class AttendanceAdjustment {

    public enum Type { CREATE, EDIT }

    public enum Status { PENDING, APPLIED, REJECTED, CANCELLED }

    @Id
    private UUID id = Ids.newId();

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "work_date", nullable = false, updatable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "adj_type", nullable = false, updatable = false)
    private Type type;

    @Column(name = "new_in", updatable = false)
    private LocalDateTime newIn;

    @Column(name = "new_out", updatable = false)
    private LocalDateTime newOut;

    @Column(name = "before_in", updatable = false)
    private LocalDateTime beforeIn;

    @Column(name = "before_out", updatable = false)
    private LocalDateTime beforeOut;

    @Enumerated(EnumType.STRING)
    @Column(name = "before_status", updatable = false)
    private DayStatus beforeStatus;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "override_lock", nullable = false, updatable = false)
    private boolean overrideLock;

    @Column(name = "approval_id")
    private UUID approvalId;

    @Column(name = "requested_by", updatable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected AttendanceAdjustment() {
    }

    public AttendanceAdjustment(UUID employeeId, UUID companyId, LocalDate workDate, Type type, LocalDateTime newIn,
            LocalDateTime newOut, LocalDateTime beforeIn, LocalDateTime beforeOut, DayStatus beforeStatus,
            String reason, boolean overrideLock, UUID requestedBy, Instant requestedAt) {
        this.employeeId = employeeId;
        this.companyId = companyId;
        this.workDate = workDate;
        this.type = type;
        this.newIn = newIn;
        this.newOut = newOut;
        this.beforeIn = beforeIn;
        this.beforeOut = beforeOut;
        this.beforeStatus = beforeStatus;
        this.reason = reason;
        this.overrideLock = overrideLock;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
        this.status = Status.PENDING;
    }

    public void decide(Status outcome, Instant when) {
        this.status = outcome;
        this.decidedAt = when;
    }

    public void setApprovalId(UUID approvalId) {
        this.approvalId = approvalId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public Type getType() {
        return type;
    }

    public LocalDateTime getNewIn() {
        return newIn;
    }

    public LocalDateTime getNewOut() {
        return newOut;
    }

    public LocalDateTime getBeforeIn() {
        return beforeIn;
    }

    public LocalDateTime getBeforeOut() {
        return beforeOut;
    }

    public DayStatus getBeforeStatus() {
        return beforeStatus;
    }

    public String getReason() {
        return reason;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isOverrideLock() {
        return overrideLock;
    }

    public UUID getApprovalId() {
        return approvalId;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
