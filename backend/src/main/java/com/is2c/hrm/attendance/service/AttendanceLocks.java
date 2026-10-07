package com.is2c.hrm.attendance.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.is2c.hrm.attendance.api.AttendanceLockPolicy;
import com.is2c.hrm.common.error.LockedException;

/** Convenience wrapper over the optional payroll lock policy (FR-053). */
@Component
public class AttendanceLocks {

    public static final String CODE = "ATTENDANCE_LOCKED";

    private final ObjectProvider<AttendanceLockPolicy> policy;

    public AttendanceLocks(ObjectProvider<AttendanceLockPolicy> policy) {
        this.policy = policy;
    }

    public boolean isLocked(UUID companyId, LocalDate date) {
        AttendanceLockPolicy p = policy.getIfAvailable();
        return p != null && p.isLocked(companyId, date);
    }

    public void assertUnlocked(UUID companyId, Collection<LocalDate> dates) {
        for (LocalDate d : dates) {
            if (isLocked(companyId, d)) {
                throw new LockedException(CODE, "Attendance on " + d + " is locked by payroll");
            }
        }
    }

    public void changed(UUID companyId, Collection<LocalDate> dates) {
        AttendanceLockPolicy p = policy.getIfAvailable();
        if (p != null && !dates.isEmpty()) {
            p.attendanceChanged(companyId, dates);
        }
    }
}
