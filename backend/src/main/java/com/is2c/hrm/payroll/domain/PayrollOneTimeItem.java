package com.is2c.hrm.payroll.domain;

import java.math.BigDecimal;
import java.util.UUID;

import com.is2c.hrm.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Bonus, incentive, commission or other one-off amount for a period (FR-083). */
@Entity
@Table(name = "payroll_one_time_item")
public class PayrollOneTimeItem extends BaseEntity {

    @Column(name = "period_id", nullable = false, updatable = false)
    private UUID periodId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "component_id", nullable = false)
    private UUID componentId;

    @Column(nullable = false)
    private BigDecimal amount;

    private String note;

    protected PayrollOneTimeItem() {
    }

    public PayrollOneTimeItem(UUID periodId, UUID employeeId, UUID componentId, BigDecimal amount, String note) {
        this.periodId = periodId;
        this.employeeId = employeeId;
        this.componentId = componentId;
        this.amount = amount;
        this.note = note;
    }

    public UUID getPeriodId() {
        return periodId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public UUID getComponentId() {
        return componentId;
    }

    public void setComponentId(UUID componentId) {
        this.componentId = componentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
