package com.is2c.hrm.employee.api;

import java.util.UUID;

/** Implemented by modules holding employee transactions (attendance, leave, payroll) to block hard removal (FR-017). */
public interface EmployeeUsageChecker {

    boolean hasRecords(UUID employeeId);
}
