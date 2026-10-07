package com.is2c.hrm.payroll.domain;

import java.math.BigDecimal;
import java.util.UUID;

import com.is2c.hrm.common.domain.Ids;
import com.is2c.hrm.payroll.api.ComponentKind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Snapshot of one component's result for an employee (FR-085). */
@Entity
@Table(name = "payroll_result_line")
public class PayrollResultLine {

    @Id
    private UUID id = Ids.newId();

    @Column(name = "result_id", nullable = false, updatable = false)
    private UUID resultId;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @Column(name = "component_code", nullable = false, updatable = false)
    private String componentCode;

    @Column(name = "component_name", nullable = false, updatable = false)
    private String componentName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ComponentKind kind;

    @Column(updatable = false)
    private BigDecimal units;

    @Column(updatable = false)
    private BigDecimal rate;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "show_on_payslip", nullable = false, updatable = false)
    private boolean showOnPayslip;

    @Column(nullable = false)
    private boolean locked;

    protected PayrollResultLine() {
    }

    public PayrollResultLine(UUID resultId, int sortOrder, String componentCode, String componentName,
            ComponentKind kind, BigDecimal units, BigDecimal rate, BigDecimal amount, boolean showOnPayslip) {
        this.resultId = resultId;
        this.sortOrder = sortOrder;
        this.componentCode = componentCode;
        this.componentName = componentName;
        this.kind = kind;
        this.units = units;
        this.rate = rate;
        this.amount = amount;
        this.showOnPayslip = showOnPayslip;
    }

    public void lock() {
        this.locked = true;
    }

    public UUID getResultId() {
        return resultId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getComponentCode() {
        return componentCode;
    }

    public String getComponentName() {
        return componentName;
    }

    public ComponentKind getKind() {
        return kind;
    }

    public BigDecimal getUnits() {
        return units;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean isShowOnPayslip() {
        return showOnPayslip;
    }

    public boolean isLocked() {
        return locked;
    }
}
