package com.is2c.hrm.org.api;

import java.util.UUID;

/** Where an employee sits in the organization; the unit of data-scope checks (FR-006). */
public record EmployeePlacement(UUID employeeId, UUID companyId, UUID branchId, UUID departmentId) {
}
