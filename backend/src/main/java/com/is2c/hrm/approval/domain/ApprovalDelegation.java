package com.is2c.hrm.approval.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.is2c.hrm.common.domain.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Temporary hand-over of approval rights (FR-073). Not transitive. */
@Entity
@Table(name = "approval_delegation")
public class ApprovalDelegation {

    @Id
    private UUID id = Ids.newId();

    @Column(name = "delegator_user_id", nullable = false)
    private UUID delegatorUserId;

    @Column(name = "delegate_user_id", nullable = false)
    private UUID delegateUserId;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ApprovalDelegation() {
    }

    public ApprovalDelegation(UUID delegatorUserId, UUID delegateUserId, LocalDate fromDate, LocalDate toDate,
            Instant createdAt) {
        this.delegatorUserId = delegatorUserId;
        this.delegateUserId = delegateUserId;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.createdAt = createdAt;
    }

    public boolean activeOn(LocalDate date) {
        return active && !date.isBefore(fromDate) && !date.isAfter(toDate);
    }

    public boolean overlaps(LocalDate from, LocalDate to) {
        return active && !from.isAfter(toDate) && !to.isBefore(fromDate);
    }

    public void deactivate() {
        active = false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDelegatorUserId() {
        return delegatorUserId;
    }

    public UUID getDelegateUserId() {
        return delegateUserId;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public boolean isActive() {
        return active;
    }
}
