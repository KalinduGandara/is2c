package com.is2c.hrm.payroll.domain;

import java.math.BigDecimal;
import java.util.UUID;

import com.is2c.hrm.common.domain.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Employee payroll result with a snapshot of names; immutable once locked (FR-085, FR-087). */
@Entity
@Table(name = "payroll_result")
public class PayrollResult {

    @Id
    private UUID id = Ids.newId();

    @Column(name = "period_id", nullable = false, updatable = false)
    private UUID periodId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "employee_number", nullable = false, updatable = false)
    private String employeeNumber;

    @Column(name = "employee_name", nullable = false, updatable = false)
    private String employeeName;

    @Column(name = "department_name", updatable = false)
    private String departmentName;

    @Column(name = "designation_name", updatable = false)
    private String designationName;

    @Column(name = "department_id", updatable = false)
    private UUID departmentId;

    @Column(name = "branch_id", updatable = false)
    private UUID branchId;

    @Column(nullable = false, updatable = false)
    private BigDecimal gross;

    @Column(nullable = false, updatable = false)
    private BigDecimal deductions;

    @Column(nullable = false, updatable = false)
    private BigDecimal net;

    @Column(name = "employer_contributions", nullable = false, updatable = false)
    private BigDecimal employerContributions;

    @Column(name = "worked_days", nullable = false, updatable = false)
    private BigDecimal workedDays;

    @Column(name = "no_pay_days", nullable = false, updatable = false)
    private BigDecimal noPayDays;

    @Column(name = "ot_hours", nullable = false, updatable = false)
    private BigDecimal otHours;

    @Column(name = "payslip_no")
    private String payslipNo;

    @Column(nullable = false)
    private boolean locked;

    protected PayrollResult() {
    }

    public PayrollResult(UUID periodId, UUID employeeId, String employeeNumber, String employeeName,
            String departmentName, String designationName, UUID branchId, UUID departmentId, BigDecimal gross, BigDecimal deductions,
            BigDecimal net, BigDecimal employerContributions, BigDecimal workedDays, BigDecimal noPayDays,
            BigDecimal otHours) {
        this.periodId = periodId;
        this.employeeId = employeeId;
        this.employeeNumber = employeeNumber;
        this.employeeName = employeeName;
        this.departmentName = departmentName;
        this.designationName = designationName;
        this.branchId = branchId;
        this.departmentId = departmentId;
        this.gross = gross;
        this.deductions = deductions;
        this.net = net;
        this.employerContributions = employerContributions;
        this.workedDays = workedDays;
        this.noPayDays = noPayDays;
        this.otHours = otHours;
    }

    public void lock(String payslipNumber) {
        this.payslipNo = payslipNumber;
        this.locked = true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPeriodId() {
        return periodId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public String getDesignationName() {
        return designationName;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public BigDecimal getGross() {
        return gross;
    }

    public BigDecimal getDeductions() {
        return deductions;
    }

    public BigDecimal getNet() {
        return net;
    }

    public BigDecimal getEmployerContributions() {
        return employerContributions;
    }

    public BigDecimal getWorkedDays() {
        return workedDays;
    }

    public BigDecimal getNoPayDays() {
        return noPayDays;
    }

    public BigDecimal getOtHours() {
        return otHours;
    }

    public String getPayslipNo() {
        return payslipNo;
    }

    public boolean isLocked() {
        return locked;
    }
}
