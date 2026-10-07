package com.is2c.hrm.roster.api;

import java.time.LocalDate;
import java.util.UUID;

import com.is2c.hrm.shift.api.ShiftDefinition;

/** Expected schedule of one employee on one date (FR-033). {@code shift} is null for OFF/unscheduled days. */
public record DaySchedule(UUID employeeId, LocalDate date, DayType dayType, ShiftDefinition shift, Source source,
        String holidayName) {

    public enum DayType { WORKDAY, WEEKLY_OFF, HOLIDAY }

    /** Where the schedule came from, in precedence order. */
    public enum Source { ROSTER, ROTATION, DEFAULT }

    public boolean isUnscheduledWorkday() {
        return dayType == DayType.WORKDAY && shift == null;
    }
}
