package com.is2c.hrm.payroll.domain;

import java.util.UUID;

import com.is2c.hrm.common.domain.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Issue found while processing a period (FR-085); blocking ones prevent submission for review. */
@Entity
@Table(name = "payroll_exception")
public class PayrollException {

    @Id
    private UUID id = Ids.newId();

    @Column(name = "period_id", nullable = false)
    private UUID periodId;

    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private boolean blocking;

    protected PayrollException() {
    }

    public PayrollException(UUID periodId, UUID employeeId, String code, String message, boolean blocking) {
        this.periodId = periodId;
        this.employeeId = employeeId;
        this.code = code;
        this.message = message;
        this.blocking = blocking;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public boolean isBlocking() {
        return blocking;
    }
}
