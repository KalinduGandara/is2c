package com.is2c.hrm.leave.domain;

import java.math.BigDecimal;
import java.util.UUID;

import com.is2c.hrm.common.domain.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Employee balance for one leave type and calendar year (FR-061). */
@Entity
@Table(name = "leave_balance")
public class LeaveBalance {

    @Id
    private UUID id = Ids.newId();

    @Version
    private Long version;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "leave_type_id", nullable = false, updatable = false)
    private UUID leaveTypeId;

    @Column(name = "leave_year", nullable = false, updatable = false)
    private int year;

    @Column(nullable = false)
    private BigDecimal opening = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal entitled = BigDecimal.ZERO;

    @Column(name = "carried_forward", nullable = false)
    private BigDecimal carriedForward = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal adjustment = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal taken = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal pending = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal expired = BigDecimal.ZERO;

    @Column(name = "last_accrual_month")
    private Integer lastAccrualMonth;

    protected LeaveBalance() {
    }

    public LeaveBalance(UUID employeeId, UUID leaveTypeId, int year) {
        this.employeeId = employeeId;
        this.leaveTypeId = leaveTypeId;
        this.year = year;
    }

    public BigDecimal available() {
        return opening.add(entitled).add(carriedForward).add(adjustment).subtract(taken).subtract(pending)
                .subtract(expired);
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public UUID getLeaveTypeId() {
        return leaveTypeId;
    }

    public int getYear() {
        return year;
    }

    public BigDecimal getOpening() {
        return opening;
    }

    public void setOpening(BigDecimal opening) {
        this.opening = opening;
    }

    public BigDecimal getEntitled() {
        return entitled;
    }

    public void addEntitled(BigDecimal days) {
        this.entitled = entitled.add(days);
    }

    public BigDecimal getCarriedForward() {
        return carriedForward;
    }

    public void setCarriedForward(BigDecimal carriedForward) {
        this.carriedForward = carriedForward;
    }

    public BigDecimal getAdjustment() {
        return adjustment;
    }

    public void addAdjustment(BigDecimal days) {
        this.adjustment = adjustment.add(days);
    }

    public BigDecimal getTaken() {
        return taken;
    }

    public void addTaken(BigDecimal days) {
        this.taken = taken.add(days);
    }

    public BigDecimal getPending() {
        return pending;
    }

    public void addPending(BigDecimal days) {
        this.pending = pending.add(days);
    }

    public BigDecimal getExpired() {
        return expired;
    }

    public void addExpired(BigDecimal days) {
        this.expired = expired.add(days);
    }

    public Integer getLastAccrualMonth() {
        return lastAccrualMonth;
    }

    public void setLastAccrualMonth(Integer lastAccrualMonth) {
        this.lastAccrualMonth = lastAccrualMonth;
    }
}
