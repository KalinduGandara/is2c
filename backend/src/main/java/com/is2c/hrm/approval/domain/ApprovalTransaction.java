package com.is2c.hrm.approval.domain;

import java.time.Instant;
import java.util.UUID;

import com.is2c.hrm.common.domain.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** One item moving through an approval workflow (FR-071). Levels are a JSON snapshot taken at submission. */
@Entity
@Table(name = "approval_transaction")
public class ApprovalTransaction {

    public enum Status { PENDING, APPROVED, REJECTED, CANCELLED }

    @Id
    private UUID id = Ids.newId();

    @Version
    private Long version;

    @Column(name = "txn_type", nullable = false, updatable = false)
    private String txnType;

    @Column(name = "reference_id", nullable = false, updatable = false)
    private UUID referenceId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "requester_user_id", updatable = false)
    private UUID requesterUserId;

    @Column(name = "subject_employee_id", updatable = false)
    private UUID subjectEmployeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "current_level", nullable = false)
    private int currentLevel = 1;

    @Column(name = "levels_snapshot", nullable = false, updatable = false)
    private String levelsSnapshot;

    @Column(nullable = false, updatable = false)
    private String summary;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ApprovalTransaction() {
    }

    public ApprovalTransaction(String txnType, UUID referenceId, UUID companyId, UUID requesterUserId,
            UUID subjectEmployeeId, String levelsSnapshot, String summary, Instant submittedAt) {
        this.txnType = txnType;
        this.referenceId = referenceId;
        this.companyId = companyId;
        this.requesterUserId = requesterUserId;
        this.subjectEmployeeId = subjectEmployeeId;
        this.levelsSnapshot = levelsSnapshot;
        this.summary = summary;
        this.submittedAt = submittedAt;
    }

    public void complete(Status outcome, Instant when) {
        this.status = outcome;
        this.completedAt = when;
    }

    public void advance() {
        currentLevel++;
    }

    public UUID getId() {
        return id;
    }

    public String getTxnType() {
        return txnType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getRequesterUserId() {
        return requesterUserId;
    }

    public UUID getSubjectEmployeeId() {
        return subjectEmployeeId;
    }

    public Status getStatus() {
        return status;
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    public String getLevelsSnapshot() {
        return levelsSnapshot;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
