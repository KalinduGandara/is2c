package com.is2c.hrm.common.error;

import java.util.List;

/** Data is locked by a payroll stage (ATTENDANCE_LOCKED, PAYROLL_LOCKED): 422. */
public class LockedException extends ValidationException {

    public LockedException(String code, String message) {
        super(code, message, List.of());
    }
}
