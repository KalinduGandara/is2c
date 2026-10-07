package com.is2c.hrm.payroll.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.is2c.hrm.common.domain.BaseEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/** Effective-dated salary of an employee (FR-082). */
@Entity
@Table(name = "salary_structure")
public class SalaryStructure extends BaseEntity {

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "basic_salary", nullable = false, updatable = false)
    private BigDecimal basicSalary;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "salary_structure_line", joinColumns = @JoinColumn(name = "structure_id"))
    @OrderColumn(name = "line_index")
    private List<StructureLine> lines = new ArrayList<>();

    protected SalaryStructure() {
    }

    public SalaryStructure(UUID employeeId, UUID companyId, LocalDate effectiveFrom, BigDecimal basicSalary) {
        this.employeeId = employeeId;
        this.companyId = companyId;
        this.effectiveFrom = effectiveFrom;
        this.basicSalary = basicSalary;
    }

    public boolean coversDate(LocalDate date) {
        return !date.isBefore(effectiveFrom) && (effectiveTo == null || !date.isAfter(effectiveTo));
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public BigDecimal getBasicSalary() {
        return basicSalary;
    }

    public List<StructureLine> getLines() {
        return lines;
    }
}
