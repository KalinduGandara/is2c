package com.is2c.hrm.payroll.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.is2c.hrm.employee.api.EmployeeUsageChecker;
import com.is2c.hrm.payroll.domain.SalaryStructureRepository;

/** FR-017: employees with payroll data cannot be deleted. */
@Component
public class PayrollUsageChecker implements EmployeeUsageChecker {

    private final SalaryStructureRepository structures;

    public PayrollUsageChecker(SalaryStructureRepository structures) {
        this.structures = structures;
    }

    @Override
    public boolean hasRecords(UUID employeeId) {
        return structures.existsByEmployeeId(employeeId);
    }
}
