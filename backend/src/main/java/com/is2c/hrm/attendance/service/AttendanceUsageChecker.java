package com.is2c.hrm.attendance.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.is2c.hrm.attendance.domain.AttendanceEventRepository;
import com.is2c.hrm.employee.api.EmployeeUsageChecker;

/** FR-017: employees with attendance data cannot be deleted. */
@Component
public class AttendanceUsageChecker implements EmployeeUsageChecker {

    private final AttendanceEventRepository events;

    public AttendanceUsageChecker(AttendanceEventRepository events) {
        this.events = events;
    }

    @Override
    public boolean hasRecords(UUID employeeId) {
        return events.existsByEmployeeId(employeeId);
    }
}
