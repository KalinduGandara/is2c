package com.is2c.hrm.attendance.api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import com.is2c.hrm.attendance.service.AttendanceCalculator.LeaveInfo;

/** Implemented by the leave module: approved leave per employee and date (FR-064). */
public interface ApprovedLeaveLookup {

    record Key(UUID employeeId, LocalDate date) {
    }

    Map<Key, LeaveInfo> find(Collection<UUID> employeeIds, LocalDate from, LocalDate to);
}
