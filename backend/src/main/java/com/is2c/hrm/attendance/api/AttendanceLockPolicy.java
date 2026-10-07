package com.is2c.hrm.attendance.api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;

/** Implemented by payroll: which dates are locked by payroll stages, and stale marking of draft periods (FR-053). */
public interface AttendanceLockPolicy {

    boolean isLocked(UUID companyId, LocalDate date);

    /** Attendance on these dates changed; draft payroll periods covering them become stale. */
    void attendanceChanged(UUID companyId, Collection<LocalDate> dates);
}
