package com.is2c.hrm.leave.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.is2c.hrm.employee.api.EmployeeUsageChecker;
import com.is2c.hrm.leave.domain.LeaveBalanceRepository;

/** FR-017: employees with leave records cannot be deleted. */
@Component
public class LeaveUsageChecker implements EmployeeUsageChecker {

    private final LeaveBalanceRepository balances;

    public LeaveUsageChecker(LeaveBalanceRepository balances) {
        this.balances = balances;
    }

    @Override
    public boolean hasRecords(UUID employeeId) {
        return balances.existsByEmployeeId(employeeId);
    }
}
