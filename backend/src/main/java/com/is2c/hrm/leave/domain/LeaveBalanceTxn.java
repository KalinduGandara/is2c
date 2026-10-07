package com.is2c.hrm.leave.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import com.is2c.hrm.common.domain.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Ledger entry explaining every balance movement; also the idempotency key for jobs (FR-062). */
@Entity
@Immutable
@Table(name = "leave_balance_txn")
public class LeaveBalanceTxn {

    public enum Type { OPENING, ACCRUAL, CARRY_FORWARD, EXPIRY, ADJUSTMENT, PENDING, RELEASE, TAKEN, RETURN }

    @Id
    private UUID id = Ids.newId();

    @Column(name = "balance_id", nullable = false)
    private UUID balanceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false)
    private Type type;

    @Column(nullable = false)
    private BigDecimal days;

    private String reference;

    private String reason;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LeaveBalanceTxn() {
    }

    public LeaveBalanceTxn(UUID balanceId, Type type, BigDecimal days, String reference, String reason, UUID createdBy,
            Instant createdAt) {
        this.balanceId = balanceId;
        this.type = type;
        this.days = days;
        this.reference = reference;
        this.reason = reason;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Type getType() {
        return type;
    }

    public BigDecimal getDays() {
        return days;
    }

    public String getReference() {
        return reference;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
