package com.is2c.hrm.leave.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.is2c.hrm.common.domain.Ids;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Leave application (FR-063). */
@Entity
@Table(name = "leave_request")
public class LeaveRequest {

    public enum Status { PENDING, APPROVED, REJECTED, CANCELLED }

    public enum HalfDayPart { MORNING, AFTERNOON }

    @Embeddable
    public record Day(@Column(name = "leave_date", nullable = false) LocalDate date,
            @Column(name = "fraction", nullable = false) BigDecimal fraction,
            @Enumerated(EnumType.STRING) @Column(name = "part") HalfDayPart part) {
    }

    @Id
    private UUID id = Ids.newId();

    @Version
    private Long version;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "leave_type_id", nullable = false, updatable = false)
    private UUID leaveTypeId;

    @Column(name = "from_date", nullable = false, updatable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false, updatable = false)
    private LocalDate toDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "half_day_part", updatable = false)
    private HalfDayPart halfDayPart;

    @Column(nullable = false, updatable = false)
    private BigDecimal days;

    @Column(updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "approval_id")
    private UUID approvalId;

    @Column(name = "applied_by", updatable = false)
    private UUID appliedBy;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note")
    private String decisionNote;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "leave_request_day", joinColumns = @JoinColumn(name = "request_id"))
    private List<Day> leaveDays = new ArrayList<>();

    protected LeaveRequest() {
    }

    public LeaveRequest(UUID employeeId, UUID companyId, UUID leaveTypeId, LocalDate fromDate, LocalDate toDate,
            HalfDayPart halfDayPart, BigDecimal days, String reason, UUID appliedBy, Instant appliedAt) {
        this.employeeId = employeeId;
        this.companyId = companyId;
        this.leaveTypeId = leaveTypeId;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.halfDayPart = halfDayPart;
        this.days = days;
        this.reason = reason;
        this.appliedBy = appliedBy;
        this.appliedAt = appliedAt;
    }

    public void decide(Status outcome, Instant when, String note) {
        this.status = outcome;
        this.decidedAt = when;
        this.decisionNote = note;
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

    public UUID getLeaveTypeId() {
        return leaveTypeId;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public HalfDayPart getHalfDayPart() {
        return halfDayPart;
    }

    public BigDecimal getDays() {
        return days;
    }

    public String getReason() {
        return reason;
    }

    public Status getStatus() {
        return status;
    }

    public UUID getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(UUID approvalId) {
        this.approvalId = approvalId;
    }

    public UUID getAppliedBy() {
        return appliedBy;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public List<Day> getLeaveDays() {
        return leaveDays;
    }
}
