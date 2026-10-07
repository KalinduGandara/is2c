package com.is2c.hrm.payroll.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.is2c.hrm.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Monthly payroll cycle (FR-084). */
@Entity
@Table(name = "payroll_period")
public class PayrollPeriod extends BaseEntity {

    public enum Status { OPEN, DRAFT, IN_REVIEW, APPROVED, FINALIZED }

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "period_year", nullable = false, updatable = false)
    private int year;

    @Column(name = "period_month", nullable = false, updatable = false)
    private int month;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Column(nullable = false)
    private boolean stale;

    @Column(name = "approval_id")
    private UUID approvalId;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "finalized_by")
    private UUID finalizedBy;

    protected PayrollPeriod() {
    }

    public PayrollPeriod(UUID companyId, int year, int month, LocalDate startDate, LocalDate endDate) {
        this.companyId = companyId;
        this.year = year;
        this.month = month;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    /** Attendance and leave are locked from review onwards (FR-053). */
    public boolean locksAttendance() {
        return status == Status.IN_REVIEW || status == Status.APPROVED || status == Status.FINALIZED;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public int getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public boolean isStale() {
        return stale;
    }

    public void setStale(boolean stale) {
        this.stale = stale;
    }

    public UUID getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(UUID approvalId) {
        this.approvalId = approvalId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public UUID getFinalizedBy() {
        return finalizedBy;
    }

    public void finalize(Instant when, UUID by) {
        this.status = Status.FINALIZED;
        this.finalizedAt = when;
        this.finalizedBy = by;
    }
}
